import { Request, Response, Router } from "express";
import { z } from "zod";
import { asyncHandler } from "../utils/http";
import { getPptDefaultInstructions, summarizeReportForPpt } from "../../services/analysis-agent/delivery/ppt-summarizer";

const router: ReturnType<typeof Router> = Router();

router.get("/defaults", (_req: Request, res: Response) => {
  res.json({ success: true, data: { instructions: getPptDefaultInstructions() } });
});

const PptSummaryBodySchema = z.object({
  title: z.string().trim().min(1).max(200),
  userInstructions: z.string().trim().max(10_000).optional().default(""),
  cover: z
    .object({ title: z.string().trim().max(200), subtitle: z.string().trim().max(500) })
    .optional(),
  report: z.string().trim().min(1).max(500_000),
  sections: z
    .array(
      z.object({
        id: z.string().min(1).max(200),
        heading: z.string().max(300),
        text: z.string().max(30_000),
        hasChart: z.boolean(),
      })
    )
    .min(1)
    .max(50),
});

router.post(
  "/summarize",
  asyncHandler(async (req: Request, res: Response) => {
    const input = PptSummaryBodySchema.parse(req.body);
    const outline = await summarizeReportForPpt(input);
    res.json({ success: true, data: outline });
  })
);

export default router;
