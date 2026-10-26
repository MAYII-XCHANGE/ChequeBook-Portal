import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TABLE_PAGE_SIZE, usePagination } from "./usePagination";

describe("usePagination", () => {
  it("shows ten rows per page by default", () => {
    const items = Array.from({ length: 23 }, (_, index) => index + 1);
    const { result } = renderHook(() => usePagination(items));

    expect(TABLE_PAGE_SIZE).toBe(10);
    expect(result.current.paginatedItems).toEqual(items.slice(0, 10));
    expect(result.current.totalPages).toBe(3);

    act(() => result.current.setCurrentPage(2));

    expect(result.current.paginatedItems).toEqual(items.slice(10, 20));
  });
});
