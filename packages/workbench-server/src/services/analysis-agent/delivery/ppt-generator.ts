import type pptxgen from "pptxgenjs";
import { createRequire } from "node:module";
import { workbenchProduct } from "../../../product/installed";
import type { PptSummarySlide, PptSummaryOutline, PptGenerationOptions, PptSourceSection } from "@ontomato/contracts/ppt";
import { marked, type Tokens } from "marked";
import { renderChartHtmlToDataUrl } from "./chart-renderer";
import { summarizeReportForPpt } from "./ppt-summarizer";

// Use the Node entry explicitly: the package's ESM entry is wrapped by tsx in development.
const PptxGen = createRequire(import.meta.url)("pptxgenjs") as typeof pptxgen;

export function stripPptCharts(text: string): string {
  return text.replace(/```html\s*[\s\S]*?```/g, "").replace(/^\s*\[chart:[^\]\r\n]+\]\s*$/gm, "").trim();
}

export async function generatePpt(title: string, sections: readonly PptSourceSection[], options: PptGenerationOptions = {}, signal?: AbortSignal) {
  const outline = await summarizeReportForPpt({ title, ...options,
    report: "",
    sections: sections.map(section => ({id: section.id, heading: section.heading || section.content.match(/^#{1,3}\s+(.+)$/m)?.[1] || title,
      text: stripPptCharts(section.content), hasChart: /```html/.test(section.content)})),
  }, signal);
  return buildPptDeck(title, sections, outline, options, signal);
}

function toDataUrl(svg: string): string {
  return `data:image/svg+xml;base64,${Buffer.from(svg).toString("base64")}`;
}

function buildIllustration(visual: PptSummarySlide["visual"], accent: string): string {
  const shapes = {
    insight: `<path d="M28 122h44V78h44v44h44V48h44v74h44" fill="none" stroke="${accent}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/><circle cx="94" cy="78" r="10" fill="${accent}"/><circle cx="182" cy="48" r="10" fill="${accent}"/>`,
    risk: `<path d="M128 25 232 210H24L128 25Z" fill="#FFF3E8" stroke="${accent}" stroke-width="10"/><path d="M128 82v58M128 169v3" stroke="${accent}" stroke-width="13" stroke-linecap="round"/>`,
    action: `<path d="M36 184 98 122l34 34 86-102" fill="none" stroke="${accent}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/><path d="m181 54 37 0-1 37" fill="none" stroke="${accent}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>`,
    metric: `<rect x="30" y="42" width="196" height="150" rx="18" fill="#EEF4FF" stroke="${accent}" stroke-width="9"/><path d="M64 151h31v-47h31v47h31V80h31v71" fill="none" stroke="${accent}" stroke-width="11" stroke-linejoin="round"/>`,
  } as const;
  return toDataUrl(
    `<svg xmlns="http://www.w3.org/2000/svg" width="256" height="240" viewBox="0 0 256 240"><rect width="256" height="240" rx="28" fill="#F4F7FC"/>${shapes[visual === "chart" ? "insight" : visual]}</svg>`
  );
}

function addSlideChrome(
  slide: pptxgen.Slide,
  shapeType: typeof pptxgen.ShapeType,
  title: string
) {
  slide.background = { color: "F7F9FC" };
  slide.addShape(shapeType.rect, {
    x: 0,
    y: 0,
    w: 13.333,
    h: 0.12,
    line: { color: "2457D6", transparency: 100 },
    fill: { color: "2457D6" },
  });
  slide.addText(title, {
    x: 0.55,
    y: 7.08,
    w: 9.6,
    h: 0.2,
    fontFace: "Microsoft YaHei",
    fontSize: 8,
    color: "667085",
    margin: 0,
  });

}

export async function buildPptDeck(
  title: string, sections: readonly PptSourceSection[], outline: PptSummaryOutline,
  options: PptGenerationOptions = {}, signal?: AbortSignal,
  renderChart: (html: string) => Promise<string> = html => renderChartHtmlToDataUrl(html, 30000, {offline: true, signal})
): Promise<{ buffer: Buffer; warnings: string[] }> {
  const warnings: string[] = [];
  const sectionCharts = new Map<string, string>();
  const usedCharts = new Set<string>();
  for (const section of sections) {
    const chart = [...section.content.matchAll(/```html\s*\n([\s\S]*?)\n```/g)][0]?.[1];
    if (chart) sectionCharts.set(section.id, chart.trim());
  }
  const deck = new PptxGen();
  deck.layout = "LAYOUT_WIDE";
  deck.author = workbenchProduct().serviceDisplayName;
  deck.subject = title;
  deck.title = title;
  deck.company = workbenchProduct().serviceDisplayName;
  deck.theme = {
    headFontFace: "Microsoft YaHei",
    bodyFontFace: "Microsoft YaHei",
  };
  deck.defineSlideMaster({
    title: "REPORT",
    background: { color: "F7F9FC" },
    objects: [],
    slideNumber: { x: 12.1, y: 7.08, color: "667085", fontFace: "Arial", fontSize: 8 },
  });

  const totalSlides = outline.slides.length + 1;
  for (let index = 0; index < totalSlides; index += 1) {
    const draft = outline.slides[index - 1];
    const slide = deck.addSlide("REPORT");
    addSlideChrome(slide, deck.ShapeType, outline.title);
    if (index === 0) {
      slide.background = { color: "172033" };
      slide.addShape(deck.ShapeType.rect, {
        x: 0,
        y: 0,
        w: 13.333,
        h: 7.5,
        line: { color: "172033", transparency: 100 },
        fill: { color: "172033" },
      });
      if (options.coverImage && options.coverImageMode !== "background") {
        slide.addImage({ data: options.coverImage, x: 7.45, y: 1.05, w: 5.2, h: 5.35, transparency: 0 });
      } else if (options.coverImage) {
        slide.addImage({ data: options.coverImage, x: 0, y: 0, w: 13.333, h: 7.5, transparency: 42 });
        slide.addShape(deck.ShapeType.rect, {
          x: 0, y: 0, w: 13.333, h: 7.5,
          line: { color: "172033", transparency: 100 },
          fill: { color: "172033", transparency: 38 },
        });
      }
      slide.addShape(deck.ShapeType.rect, {
        x: 0.65,
        y: 0.82,
        w: 0.16,
        h: 5.65,
        line: { color: "5B8DEF", transparency: 100 },
        fill: { color: "5B8DEF" },
      });
      slide.addText(outline.title, {
        x: 1.2,
        y: 2.15,
        w: options.coverImage && options.coverImageMode !== "background" ? 5.8 : 10.6,
        h: 1.0,
        fontFace: "Microsoft YaHei",
        fontSize: outline.title.length > 24 ? 30 : 38,
        bold: true,
        color: "FFFFFF",
        fit: "shrink",
        margin: 0,
      });
      slide.addText(outline.subtitle, {
        x: 1.22,
        y: 3.42,
        w: options.coverImage && options.coverImageMode !== "background" ? 5.8 : 8.5,
        h: 1.2,
        fontFace: "Microsoft YaHei",
        fontSize: 17,
        color: "B8C5E0",
        fit: "shrink",
        margin: 0,
      });
      continue;
    }

    slide.addText(draft.title, {
      x: 0.6,
      y: 0.55,
      w: 11.4,
      h: 0.62,
      fontFace: "Microsoft YaHei",
      fontSize: draft.title.length > 28 ? 24 : 29,
      bold: true,
      color: "172033",
      breakLine: false,
      fit: "shrink",
      margin: 0,
    });
    slide.addShape(deck.ShapeType.line, {
      x: 0.6,
      y: 1.35,
      w: 1.2,
      h: 0,
      line: { color: "2457D6", width: 2.5 },
    });

    const chartHtml = draft.sourceSectionIds.map((id) => sectionCharts.get(id)).find(Boolean);
    let chartImage: {data: string; width: number; height: number} | null = null;
    if (chartHtml) {
      signal?.throwIfAborted();
      try { chartImage = { data: await renderChart(chartHtml), width: 1000, height: 500 }; }
      catch { warnings.push(`Chart could not be rendered: ${draft.title}`); }
      signal?.throwIfAborted();
    }
    if (chartHtml) usedCharts.add(chartHtml);
    const hasChart = Boolean(chartImage);
    const textWidth = hasChart ? 5.7 : 7.9;
    slide.addText(draft.takeaway, {
      x: 0.72,
      y: 1.72,
      w: textWidth,
      h: 1.0,
      fontFace: "Microsoft YaHei",
      fontSize: 22,
      bold: true,
      color: "2457D6",
      breakLine: false,
      fit: "shrink",
      valign: "top",
      margin: 0.02,
    });
    if (draft.bullets.length) {
      slide.addText(
        draft.bullets.map((bullet) => ({
          text: bullet,
          options: { bullet: { indent: 16 }, hanging: 4 },
        })),
        {
          x: 0.82,
          y: 3.0,
          w: textWidth,
          h: hasChart ? 3.0 : 3.35,
          fontFace: "Microsoft YaHei",
          fontSize: 16,
          color: "172033",
          breakLine: true,
          fit: "shrink",
          paraSpaceAfter: 12,
          margin: 0.03,
          valign: "top",
        }
      );
    }
    if (chartImage) {
      const maxWidth = 5.65;
      const maxHeight = 4.55;
      const ratio = chartImage.width / chartImage.height;
      const imageWidth = Math.min(maxWidth, maxHeight * ratio);
      const imageHeight = imageWidth / ratio;
      const imageX = 6.92 + (maxWidth - imageWidth) / 2;
      const imageY = 1.85 + (maxHeight - imageHeight) / 2;
      slide.addShape(deck.ShapeType.roundRect, {
        x: 6.75,
        y: 1.7,
        w: 5.95,
        h: 4.85,
        rectRadius: 0.05,
        line: { color: "D9DEE8", width: 0.7 },
        fill: { color: "FFFFFF" },
      });
      slide.addImage({
        data: chartImage.data,
        x: imageX,
        y: imageY,
        w: imageWidth,
        h: imageHeight,
        transparency: 0,
      });
    } else {
      slide.addImage({
        data: buildIllustration(draft.visual, index % 2 ? "2457D6" : "E8793D"),
        x: 9.35,
        y: 4.85,
        w: 2.2,
        h: 2.05,
        transparency: 6,
      });
    }
  }


  // Append every source chart and paginate native tables, even if the summary omits its section.
  for (const section of sections) {
    signal?.throwIfAborted();
    for (const match of section.content.matchAll(/```html\s*\n([\s\S]*?)\n```/g)) {
      const html = match[1].trim();
      if (usedCharts.has(html)) continue;
      usedCharts.add(html);
      const slide = deck.addSlide("REPORT");
      addSlideChrome(slide, deck.ShapeType, outline.title);
      slide.addText(section.heading || title, {x: .6, y: .4, w: 12, h: .7, fontSize: 24});
      try { slide.addImage({ data: await renderChart(html), x: .8, y: 1.3, w: 11.6, h: 5.1 }); }
      catch { warnings.push(`Chart could not be rendered: ${section.heading || section.id}`);
        slide.addText("Chart unavailable. See the source data and explanation.", {x: 1, y: 2, w: 11, h: 1, fontSize: 20}); }
      signal?.throwIfAborted();
      const description = stripPptCharts(section.content).replace(/\|.*\|/g, "").slice(0, 220);
      slide.addText(description, {x: .8, y: 6.5, w: 11.6, h: .45, fontSize: 10, fit: "shrink"});
    }
    const tables = [...(section.tables || [])];
    for (const token of marked.lexer(stripPptCharts(section.content))) {
      if (token.type === "table") {
        const table = token as Tokens.Table;
        tables.push({columns: table.header.map(cell => cell.text), rows: table.rows.map(row => row.map(cell => cell.text))});
      }
    }
    for (const table of tables) {
      if (!table.columns.length) continue;
      for (let offset = 0; offset < Math.max(1, table.rows.length); offset += 16) {
        signal?.throwIfAborted();
        const slide = deck.addSlide("REPORT");
        addSlideChrome(slide, deck.ShapeType, outline.title);
        slide.addText(section.heading || title, {x: .6, y: .4, w: 12, h: .7, fontSize: 24});
        slide.addTable([table.columns, ...table.rows.slice(offset, offset + 16)].map(row => row.map(value => ({text: typeof value === "object" && value !== null ? JSON.stringify(value) : String(value ?? "")}))),
          {x: .6, y: 1.3, w: 12.1, h: 5.4, fontSize: 10, border: {pt: .5, color: "D9DEE8"}, color: "172033", fill: {color: "FFFFFF"}, autoPage: true});
      }
    }
  }
  if (warnings.length) {
    const slide = deck.addSlide("REPORT");
    slide.addText("Export warnings", {x: .6, y: .4, w: 12, h: .7, fontSize: 24});
    slide.addText(warnings.join("\n"), {x: .7, y: 1.4, w: 12, h: 5.2, fontSize: 16, fit: "shrink"});
  }
  signal?.throwIfAborted();
  return { buffer: await deck.write({outputType: "nodebuffer"}) as Buffer, warnings };
}
