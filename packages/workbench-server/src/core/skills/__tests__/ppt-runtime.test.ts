import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterAll, describe, expect, it, vi } from "vitest";
import { installContentLayout } from "../../../content/layout";
import { configureI18n } from "../../../i18n";
import { appTextEn } from "../../../i18n/app-text-en";
import { en, enLocaleMeta } from "../../../i18n/locales/en";

vi.mock("../../../logging/logger", () => ({createLogger: () => ({debug() {}, info() {}, warn() {}, error() {}})}));
vi.mock("../../../config/model-factory", () => ({createModel: vi.fn()}));
const root = await fs.mkdtemp(path.join(os.tmpdir(), "ppt-runtime-"));
const templates = path.resolve("skills");
installContentLayout({runtimeRoot: root, prompts: [], runtimeResources: [], skillTemplate: [{from: templates, to: "."}]});
configureI18n({defaultLocale: "en", languageSwitchEnabled: false, appTextLocale: "en", packs: {en: {messages: {...en, ...appTextEn}, ...enLocaleMeta}}});
const {skillRegistryManager, DomainSkillRegistryManager} = await import("../registry/manager");
const {createLoopSkillTools, assembleLoopSkillCapability} = await import("../../../services/analysis-agent/loop/skills");
afterAll(async () => {skillRegistryManager.dispose(); await fs.rm(root, {recursive: true, force: true});});

describe("PPT Skill runtime", () => {
  it("executes the real Skill with host context, enforces selection and live disable, and settles activity", async () => {
    const registry = await skillRegistryManager.forDomain("domain");
    const generatePpt = vi.fn(async () => ({url: "/api/ppt/files/example", fileName: "conversation.pptx", warnings: []}));
    const trajectory = {start: vi.fn(() => "activity"), settle: vi.fn()};
    const deps = {domainId: "domain", selectedSkillIds: ["generate-ppt"], resolveEvidence: () => undefined, generatePpt, trajectory};
    const capability = await assembleLoopSkillCapability(deps);
    expect(capability.prompt).toContain("generate-ppt");
    const run = capability.supervisorTools.find(tool => tool.name === "run_skill")!;
    const result = await run.execute("call", {skill_id: "generate-ppt", conversation: "untrusted model history"});
    expect(result.content[0].text).toContain("conversation.pptx");
    expect(generatePpt).toHaveBeenCalledTimes(1);
    expect(generatePpt.mock.calls[0]).toEqual([undefined]);
    expect(trajectory.settle).toHaveBeenLastCalledWith("activity", "completed", expect.anything());
    registry.setSkillEnabled("generate-ppt", false);
    await run.execute("disabled", {skill_id: "generate-ppt"});
    expect(generatePpt).toHaveBeenCalledTimes(1);
    expect(trajectory.settle).toHaveBeenLastCalledWith("activity", "failed", expect.anything());
    expect((await assembleLoopSkillCapability(deps)).supervisorTools).toEqual([]);
    registry.setSkillEnabled("generate-ppt", true);
    await run.execute("restored", {skill_id: "generate-ppt"});
    expect(generatePpt).toHaveBeenCalledTimes(2);
    const unselected = createLoopSkillTools({...deps, selectedSkillIds: []}).find(tool => tool.name === "run_skill")!;
    await unselected.execute("unselected", {skill_id: "generate-ppt"});
    expect(generatePpt).toHaveBeenCalledTimes(2);
    await run.execute("invalid-entry", {skill_id: "generate-ppt", entry: "../other.mjs"});
    expect(generatePpt).toHaveBeenCalledTimes(2);
  });

  it("adds the new built-in to existing domains once and preserves other files and disabled state", async () => {
    const domains = path.join(root, "existing-domains");
    const manager = new DomainSkillRegistryManager([{from: templates, to: "."}], domains);
    const domainRoot = manager.getDomainSkillsRoot("existing");
    const states = JSON.stringify({disabledSkills: ["generate-ppt"], lastUpdated: "old"});
    await fs.mkdir(domainRoot, {recursive: true});
    await fs.writeFile(path.join(domainRoot, "skill-states.json"), states);
    await fs.writeFile(path.join(domainRoot, "custom.txt"), "unchanged");
    await manager.ensureDomainSkills("existing");
    const skillRoot = path.join(domainRoot, "executable", "analysis", "generate-ppt");
    expect(await fs.readFile(path.join(skillRoot, "SKILL.md"), "utf8")).toContain("generate-ppt");
    expect(await fs.readFile(path.join(domainRoot, "skill-states.json"), "utf8")).toBe(states);
    expect(await fs.readFile(path.join(domainRoot, "custom.txt"), "utf8")).toBe("unchanged");
    await fs.rm(skillRoot, {recursive: true});
    await manager.ensureDomainSkills("existing");
    await expect(fs.stat(skillRoot)).rejects.toMatchObject({code: "ENOENT"});
    manager.dispose();
  });
});
