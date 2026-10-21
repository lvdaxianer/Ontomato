import { describe, expect, it } from "vitest";
import {
  diffOntologyRevision,
  type OntologyRevisionContent,
} from "../ontology-versioning";

const rule = (overrides: Partial<OntologyRevisionContent> = {}): OntologyRevisionContent => ({
  classDef: [
    {
      className: "Order",
      showName: "订单",
      classDesc: "销售订单",
      attrs: [
        { name: "id", showName: "编号", type: "string" },
        { name: "amount", showName: "金额", type: "number" },
      ],
    },
  ],
  relationship_rule: {
    order_customer: {
      fromclass: "Order",
      toclass: "Customer",
      desc: "订单属于客户",
    },
  },
  ...overrides,
});

describe("ontology revision diff", () => {
  it("compares the first and current revisions by semantic keys", () => {
    const before = rule();
    const after = rule({
      classDef: [
        {
          className: "Order",
          showName: "销售订单",
          classDesc: "销售订单",
          attrs: [
            { name: "id", showName: "编号", type: "string" },
            { name: "amount", showName: "订单金额", type: "number" },
            { name: "status", showName: "状态", type: "string" },
          ],
        },
        { className: "Customer", showName: "客户", classDesc: "", attrs: [] },
      ],
    });

    const result = diffOntologyRevision(before, after);

    expect(result.objects.map((item) => [item.key, item.kind])).toEqual([
      ["Customer", "added"],
      ["Order", "changed"],
    ]);
    expect(result.attributes.map((item) => [item.key, item.kind])).toEqual([
      ["Order.amount", "changed"],
      ["Order.id", "unchanged"],
      ["Order.status", "added"],
    ]);
    expect(result.relations).toHaveLength(1);
    expect(result.relations[0]?.kind).toBe("unchanged");
    expect(result.summary).toEqual({
      added: 2,
      removed: 0,
      changed: 2,
      unchanged: 2,
      total: 6,
    });
  });

  it("reports removed classes and relations without confusing them with empty values", () => {
    const result = diffOntologyRevision(
      rule({
        classDef: [
          {
            className: "Order",
            showName: "订单",
            classDesc: "销售订单",
            attrs: [{ name: "id", showName: "编号", type: "string" }],
          },
          { className: "Customer", showName: "客户", classDesc: "", attrs: [] },
        ],
      }),
      rule({
        classDef: [
          {
            className: "Order",
            showName: "订单",
            classDesc: "销售订单",
            attrs: [{ name: "id", showName: "编号", type: "string" }],
          },
        ],
        relationship_rule: {},
      })
    );

    expect(result.objects.find((item) => item.key === "Customer")).toMatchObject({
      kind: "removed",
      before: expect.objectContaining({ className: "Customer" }),
      after: null,
    });
    expect(result.relations[0]).toMatchObject({
      key: "order_customer",
      kind: "removed",
      before: expect.objectContaining({ fromclass: "Order" }),
      after: null,
    });
    expect(result.attributes.find((item) => item.key === "Order.id")?.kind).toBe("unchanged");
  });

  it("keeps changed fields and can hide unchanged items for a focused compare view", () => {
    const result = diffOntologyRevision(
      rule(),
      rule({
        relationship_rule: {
          order_customer: {
            fromclass: "Order",
            toclass: "Customer",
            desc: "归属客户",
          },
        },
      })
    );

    const relation = result.relations[0];
    expect(relation?.kind).toBe("changed");
    expect(relation?.fields).toEqual([
      {
        field: "desc",
        before: "订单属于客户",
        after: "归属客户",
        changed: true,
      },
    ]);
    expect(result.changedItems).toEqual([
      expect.objectContaining({ key: "order_customer", kind: "changed" }),
    ]);
  });
});
