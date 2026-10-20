import { randomUUID } from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { runtimeDataDir } from "../../../content/layout";

export function safePptFileName(title: string): string {
  return (title.replace(/[\\/:*?"<>|]/g, "_").replace(/./g, (character) => character.charCodeAt(0) < 32 ? "_" : character).trim().slice(0, 80) || "conversation") + ".pptx";
}

export class PptArtifactStore {
  constructor(private readonly root: string) {}

  async save(owner: { domainId: string; userId: string }, title: string, buffer: Buffer) {
    const id = randomUUID();
    const fileName = safePptFileName(title);
    await fs.mkdir(this.root, { recursive: true });
    await fs.writeFile(path.join(this.root, `${id}.pptx`), buffer, {flag: "wx"});
    try {
      await fs.writeFile(path.join(this.root, `${id}.json`), JSON.stringify({...owner, fileName}), {flag: "wx"});
    } catch (error) {
      await fs.unlink(path.join(this.root, `${id}.pptx`));
      throw error;
    }
    return { url: `/api/ppt/files/${id}`, fileName };
  }

  async read(id: string, owner: { domainId: string; userId: string }) {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)) return null;
    try {
      const metadata = JSON.parse(await fs.readFile(path.join(this.root, `${id}.json`), "utf8"));
      if (metadata.domainId !== owner.domainId || metadata.userId !== owner.userId) return null;
      return {fileName: String(metadata.fileName), buffer: await fs.readFile(path.join(this.root, `${id}.pptx`))};
    } catch (error) {
      if ((error as { code?: string }).code === "ENOENT") return null;
      throw error;
    }
  }
}

export function pptArtifactStore() { return new PptArtifactStore(runtimeDataDir("ppt-artifacts")); }
