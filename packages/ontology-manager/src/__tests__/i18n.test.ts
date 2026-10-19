import { describe, expect, it } from "vitest";
import {
  chineseMessages,
  englishMessages,
  ontologyText,
  sharedText,
  type ManagerMessages,
} from "../i18n";

describe("message lookup", () => {
  it("provides Chinese translations for every current message key and preserves placeholders", () => {
    expect(Object.keys(chineseMessages.ontology).sort()).toEqual(
      Object.keys(englishMessages.ontology).sort()
    );
    expect(Object.keys(chineseMessages.shared).sort()).toEqual(
      Object.keys(englishMessages.shared).sort()
    );

    for (const key of Object.keys(englishMessages.ontology) as Array<keyof typeof englishMessages.ontology>) {
      const englishPlaceholders = englishMessages.ontology[key].match(/\{[^}]+\}/g) || [];
      const chinesePlaceholders = chineseMessages.ontology[key].match(/\{[^}]+\}/g) || [];
      expect(chineseMessages.ontology[key], key).not.toBe(englishMessages.ontology[key]);
      expect(chinesePlaceholders.sort(), key).toEqual(englishPlaceholders.sort());
    }

    for (const group of Object.keys(englishMessages.shared) as Array<keyof typeof englishMessages.shared>) {
      expect(Object.keys(chineseMessages.shared[group]).sort()).toEqual(
        Object.keys(englishMessages.shared[group]).sort()
      );
      const englishGroup = englishMessages.shared[group] as Readonly<Record<string, string>>;
      const chineseGroup = chineseMessages.shared[group] as Readonly<Record<string, string>>;
      for (const key of Object.keys(englishGroup)) {
        const englishValue = englishGroup[key];
        const chineseValue = chineseGroup[key];
        expect(chineseValue, `${group}.${String(key)}`).not.toBe(englishValue);
        expect((chineseValue.match(/\{[^}]+\}/g) || []).sort(), `${group}.${String(key)}`).toEqual(
          (englishValue.match(/\{[^}]+\}/g) || []).sort()
        );
      }
    }
  });

  it("reads the host-supplied table and interpolates parameters", () => {
    expect(ontologyText(englishMessages, "assetSaved", { title: "Metric view" })).toBe("Metric view saved");
    expect(sharedText(englishMessages, "dataBrowser.filterCount", { n: 2 })).toBe("Filter (2)");
  });

  it("falls back to English when an older host omits newly added messages", () => {
    const ontology: Partial<{ -readonly [Key in keyof ManagerMessages["ontology"]]: string }> = {
      ...englishMessages.ontology,
    };
    delete ontology.instanceGraphRenderProgress;
    delete ontology.searchInstanceNodes;
    const messages = { ...englishMessages, ontology } as ManagerMessages;
    expect(ontologyText(messages, "instanceGraphRenderProgress", { completed: 200, total: 1201 }))
      .toBe("Rendering graph: 200 / 1201");
    expect(ontologyText(messages, "searchInstanceNodes"))
      .toBe("Search nodes by name, ID or class");
  });

  it("preserves host translations and falls back for missing shared messages", () => {
    const messages = {
      ...englishMessages,
      ontology: { ...englishMessages.ontology, instanceGraphRenderProgress: "Progress {completed}/{total}" },
      shared: { ...englishMessages.shared, dataBrowser: {} },
    } as ManagerMessages;
    expect(ontologyText(messages, "instanceGraphRenderProgress", { completed: 1, total: 2 }))
      .toBe("Progress 1/2");
    expect(sharedText(messages, "dataBrowser.filterCount", { n: 2 })).toBe("Filter (2)");
  });
});
