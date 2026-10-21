import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import AdmZip from "adm-zip";
import { afterAll, describe, expect, it, vi } from "vitest";
import { installContentLayout } from "../../../content/layout";
import { configureI18n } from "../../../i18n";
import { appTextEn } from "../../../i18n/app-text-en";
import { en, enLocaleMeta } from "../../../i18n/locales/en";

vi.mock("../../../logging/logger", () => ({ createLogger: () => ({ debug() {}, info() {}, warn() {}, error() {} }) }));
vi.mock("../../../config/model-factory", () => ({ createModel: vi.fn() }));
vi.mock("../../analysis-agent/config/skill-references", () => ({
  listAnalysisAgentSkillReferences: vi.fn(),
  removeAnalysisAgentSkillReferences: vi.fn(),
  restoreAnalysisAgentSkillReferences: vi.fn(),
}));

const root = await fs.mkdtemp(path.join(os.tmpdir(), "public-skills-"));
const publicRoot = path.join(root, "public");
installContentLayout({ runtimeRoot: root, publicSkillsRoot: publicRoot, prompts: [], runtimeResources: [] });
configureI18n({ defaultLocale: "en", languageSwitchEnabled: false, appTextLocale: "en", packs: { en: { messages: { ...en, ...appTextEn }, ...enLocaleMeta } } });
const { skillRegistryManager } = await import("../../../core/skills/registry/manager");
const { skillRuntime } = await import("../../../core/skills/execution/runtime");
const { resolveSkillPackageFile } = await import("../../../core/skills/discovery/loader");
const { skillManager } = await import("../skill-manager");

afterAll(async () => {
  skillRegistryManager.dispose();
  await fs.rm(root, { recursive: true, force: true });
});

async function writePackage(base: string, id: string, version: string) {
  const dir = path.join(base, "executable", "analysis", id);
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(path.join(dir, "SKILL.md"), `---\nname: ${id}\ndescription: ${version}\ntype: executable\ncategory: analysis\nversion: ${version}\nentries: [index.mjs]\n---\n${version}\n`);
  await fs.writeFile(path.join(dir, "index.mjs"), `export async function execute() { return {text: ${JSON.stringify(version)}}; }\n`);
  await fs.writeFile(path.join(dir, "resource.txt"), version);
  return dir;
}

describe("public skill ownership across domains", () => {
  it("refreshes existing and new domains while preserving switches and domain-only packages", async () => {
    const oldRoot = skillRegistryManager.getDomainSkillsRoot("old");
    const oldCopy = await writePackage(oldRoot, "shared", "local-edit");
    const custom = await writePackage(oldRoot, "custom", "custom-v1");
    const states = JSON.stringify({ disabledSkills: ["shared"], lastUpdated: "unchanged" });
    await fs.writeFile(path.join(oldRoot, "skill-states.json"), states);
    const publicPackage = await writePackage(publicRoot, "shared", "1.0.0");

    const old = await skillRegistryManager.forDomain("old");
    expect(old.getSkillById("shared")).toMatchObject({ path: publicPackage, isPublic: true });
    expect(old.getSkillById("custom")).toMatchObject({ path: custom, isPublic: false });
    expect(old.isSkillEnabled("shared")).toBe(false);
    expect((await skillRegistryManager.forDomain("new")).getSkillById("shared")?.manifest.version).toBe("1.0.0");
    await expect(fs.stat(oldCopy)).rejects.toMatchObject({ code: "ENOENT" });
    const retired = await fs.readdir(path.join(oldRoot, ".trash"));
    expect(await fs.readFile(path.join(oldRoot, ".trash", retired[0], "resource.txt"), "utf8")).toBe("local-edit");

    await writePackage(publicRoot, "shared", "2.0.0");
    await writePackage(publicRoot, "added", "1.0.0");
    for (const domain of ["old", "new", "created-later"]) {
      const skills = await skillManager.getAllSkills(domain);
      expect(skills.find((skill) => skill.id === "shared")).toMatchObject({ version: "2.0.0", isPublic: true, enabled: domain !== "old" });
      expect(skills.some((skill) => skill.id === "added")).toBe(true);
      expect(skills.some((skill) => skill.id === "custom")).toBe(domain === "old");
      expect((await skillManager.getSkillContent(domain, "shared"))?.trim()).toBe("2.0.0");
      const registry = await skillRegistryManager.forDomain(domain);
      const file = await resolveSkillPackageFile(registry.getSkillById("shared")!, "resource.txt");
      expect(await fs.readFile(file, "utf8")).toBe("2.0.0");
    }
    expect(await fs.readFile(path.join(oldRoot, "skill-states.json"), "utf8")).toBe(states);
    await expect(skillRuntime.execute("old", "shared", {})).rejects.toMatchObject({ code: "SKILL_DISABLED" });
    await skillManager.toggleSkill("old", "shared");
    expect(await skillRuntime.execute("old", "shared", {})).toMatchObject({ text: "2.0.0" });
    await skillManager.toggleSkill("new", "shared");
    expect((await skillRegistryManager.forDomain("old")).isSkillEnabled("shared")).toBe(true);

    // A withdrawn public package must not resurrect the retired domain copy.
    await fs.rm(publicPackage, { recursive: true });
    expect((await skillRegistryManager.forDomain("old")).getSkillById("shared")).toBeNull();
    expect((await skillRegistryManager.forDomain("old")).getSkillById("custom")?.path).toBe(custom);
  });

  it("rejects all public-content writes and still allows domain-owned content changes", async () => {
    const dir = await writePackage(publicRoot, "protected", "1.0.0");
    const zip = new AdmZip();
    zip.addLocalFolder(dir);
    const readOnly = { status: 403, code: "PUBLIC_SKILL_READ_ONLY" };
    await expect(skillManager.createSkill("writer", "protected", "executable", "analysis", { description: "replacement" })).rejects.toMatchObject(readOnly);
    await expect(skillManager.updateSkill("writer", "protected", { skillContent: "replacement" })).rejects.toMatchObject(readOnly);
    await expect(skillManager.deleteSkill("writer", "protected")).rejects.toMatchObject(readOnly);
    for (const overwrite of [false, true]) {
      await expect(skillManager.importSkill("writer", zip.toBuffer(), overwrite)).rejects.toMatchObject(readOnly);
    }
    expect((await skillManager.getSkillContent("writer", "protected"))?.trim()).toBe("1.0.0");
    await skillManager.createSkill("writer", "mine", "knowledge", "analysis", { description: "Mine", skillContent: "before" });
    await skillManager.updateSkill("writer", "mine", { skillContent: "after" });
    expect((await skillManager.getSkillContent("writer", "mine"))?.trim()).toBe("after");
    expect((await skillManager.getAllSkills("another")).some((skill) => skill.id === "mine")).toBe(false);
  });
});
