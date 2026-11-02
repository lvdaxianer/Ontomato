package io.ontomato.dataengine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson2.JSONObject;
import io.ontomato.dataengine.config.ControllerConst;
import io.ontomato.dataengine.service.PythonFormatService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class PythonFormatController {

	@Autowired
	private PythonFormatService pythonFormatService;

	@PostMapping("/python/format")
    @ResponseBody
    public JSONObject format(@RequestHeader(ControllerConst.LANG_HEADER_KEY) String lang, @RequestBody JSONObject param) {
		JSONObject ret = new JSONObject();
		try {
			String code = param.getString("code");
			String formatted = pythonFormatService.formatPythonCode(code == null ? "" : code, lang);
			ret.put("success", true);
			ret.put("data", new JSONObject().fluentPut("code", formatted));
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			ret.put("success", false);
			ret.put("message", e.getMessage());
		}
		return ret;
	}

}
