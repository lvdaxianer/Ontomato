import { describe, expect, it } from "vitest";
import en from "../locales/en";
import zhCN from "../locales/zh-CN";

describe("open-source Chinese workbench locale", () => {
  it("preserves the English message shape and translates the primary workbench labels", () => {
    expect(Object.keys(zhCN)).toEqual(Object.keys(en));
    expect(zhCN.nav.systemManagement).toBe("系统管理");
    expect(zhCN.chat.newSession).toBe("新建会话");
    expect(zhCN.admin.embeddingDimensions).toBe("向量维度");
    expect(zhCN.common.save).toBe("保存");
  });
});
