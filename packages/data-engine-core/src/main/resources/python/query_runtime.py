# Launches one platform Python program and records whether any query it ran through this
# engine's /dsl/execute endpoint returned a partial (truncated) answer.
#
# Usage: python query_runtime.py <program.py> [program args...]
# ONTOMATO_QUERY_STATUS_FILE names the status file owned by this one run and
# ONTOMATO_ENGINE_PORT the engine's server.port. The first partial answer the program decodes
# is written to the status file; the program's own output and errors stay untouched.
import json
import os
import runpy
import sys
import urllib.request
from urllib.parse import urlsplit

STATUS_FILE = os.environ.pop("ONTOMATO_QUERY_STATUS_FILE")
ENGINE_PORT = int(os.environ.pop("ONTOMATO_ENGINE_PORT"))
ENGINE_HOSTS = ("localhost", "127.0.0.1", "::1")
LOCAL_DSL_PATHS = ("/dsl/execute", "/metricView/dsl/execute")
STATUS_KEYS = ("truncated", "rowLimit", "truncationHint")
SESSION_TOKEN = os.environ.pop("ONTOMATO_SESSION_TOKEN", "").strip()


def _is_engine_url(method, url_value):
    """
    判断请求是否指向当前运行时的本地 DSL 执行接口。

    Args:
        method: HTTP 方法。
        url_value: 请求地址。

    Returns:
        是否为当前运行时的本地 DSL 请求。

    Author: lvdaxianer@yeah.net
    Date: 2026-11-16
    """
    # 条件注释：缺少方法或地址时不能安全判断请求目标。
    if not method or not url_value:
        return False
    url = urlsplit(url_value)
    return (method.upper() == "POST" and url.hostname in ENGINE_HOSTS
            and url.port == ENGINE_PORT and url.path in LOCAL_DSL_PATHS)


def _is_engine_query(request):
    """
    判断已发送的请求是否属于需要记录结果的本地 DSL 请求。

    Args:
        request: 已发送的 HTTP 请求对象。

    Returns:
        是否为需要记录查询状态的本地 DSL 请求。

    Author: lvdaxianer@yeah.net
    Date: 2026-11-16
    """
    return _is_engine_url(request.method, request.url)


def _replace_session_headers(method, url_value, kwargs):
    """
    仅为本地 DSL 请求替换认证头，避免跨域泄漏会话凭据。

    Args:
        method: HTTP 方法。
        url_value: 请求地址。
        kwargs: requests 请求参数。

    Returns:
        处理后的 requests 请求参数。

    Author: lvdaxianer@yeah.net
    Date: 2026-11-16
    """
    # 条件注释：没有会话 Token 或请求不是本地 DSL 时保持原请求参数。
    if not SESSION_TOKEN or not _is_engine_url(method, url_value):
        return kwargs
    headers = dict(kwargs.get("headers") or {})
    headers = {key: value for key, value in headers.items()
              if key.lower() != "x-trusted-token"}
    headers["tk"] = SESSION_TOKEN
    kwargs["headers"] = headers
    return kwargs


def _record_partial_queries():
    """
    安装查询状态和本地会话认证的请求级适配器。

    Returns:
        None；requests 不可用时保持脚本原有行为。

    Author: lvdaxianer@yeah.net
    Date: 2026-11-16
    """
    try:
        import requests
    except ImportError:
        # Without requests the program cannot call /dsl/execute through the documented helper.
        return
    original_json = requests.Response.json
    original_request = requests.sessions.Session.request
    recorded = False

    def request_with_session(self, method, url_value=None, **kwargs):
        """
        为本地 DSL 请求注入当前会话 Token。

        Args:
            self: requests 会话实例。
            method: HTTP 方法。
            url_value: 请求地址，可由 requests 关键字参数提供。
            kwargs: requests 请求参数。

        Returns:
            原 requests 请求方法的响应对象。

        Author: lvdaxianer@yeah.net
        Date: 2026-11-16
        """
        # 条件注释：兼容 requests 以关键字 url 传入地址的调用形式。
        if url_value is None:
            url_value = kwargs.pop("url", None)
        return original_request(self, method, url=url_value,
                                **_replace_session_headers(method, url_value, kwargs))

    def json_with_query_status(self, **kwargs):
        nonlocal recorded
        payload = original_json(self, **kwargs)
        if not recorded and _is_engine_query(self.request):
            # The engine answers {"data": [{"answer": [...], "truncated": true, ...}]}, or {"error": ...}.
            data = payload.get("data")
            if data and data[0].get("truncated") is True:
                with open(STATUS_FILE, "w", encoding="utf-8") as status:
                    json.dump({key: data[0].get(key) for key in STATUS_KEYS}, status, ensure_ascii=False)
                recorded = True
        return payload

    requests.Response.json = json_with_query_status
    requests.sessions.Session.request = request_with_session


def _install_urllib_session_adapter():
    """
    为 urllib 发起的本地 DSL 请求注入当前会话 Token。

    Returns:
        None；仅改写当前 Python 子进程内的 urllib 请求入口。

    Author: lvdaxianer@yeah.net
    Date: 2026-11-16
    """
    original_urlopen = urllib.request.urlopen

    def urlopen_with_session(url, data=None, timeout=None,
                             *args, **kwargs):
        """
        只为当前运行时的本地 DSL 请求替换认证头。

        Args:
            url: urllib URL 或 Request 对象。
            data: 请求体。
            timeout: urllib 超时时间。
            args: 兼容 urllib 的额外位置参数。
            kwargs: 兼容 urllib 的额外关键字参数。

        Returns:
            urllib 响应对象。
        """
        request = url if isinstance(url, urllib.request.Request) else urllib.request.Request(url, data=data)
        if SESSION_TOKEN and _is_engine_url(request.get_method(), request.full_url):
            for header_name in list(request.headers):
                if header_name.lower() == "x-trusted-token":
                    request.remove_header(header_name)
            request.add_unredirected_header("tk", SESSION_TOKEN)
        if timeout is None:
            return original_urlopen(request, *args, **kwargs)
        return original_urlopen(request, timeout=timeout, *args, **kwargs)

    urllib.request.urlopen = urlopen_with_session


_record_partial_queries()
_install_urllib_session_adapter()
program = os.path.abspath(sys.argv[1])
sys.argv = sys.argv[1:]
sys.path[0] = os.path.dirname(program)
runpy.run_path(program, run_name="__main__")
