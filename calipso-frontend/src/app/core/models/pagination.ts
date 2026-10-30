export function paginationBounds(total: number, requestedPage: number, pageSize: number) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const page = Math.min(pageCount, Math.max(1, Math.trunc(requestedPage) || 1));
  const offset = (page - 1) * pageSize;
  return { page, pageCount, start: total ? offset + 1 : 0, end: Math.min(offset + pageSize, total), offset, total };
}

export function paginate<T>(items: readonly T[], page: number, pageSize: number) {
  const bounds = paginationBounds(items.length, page, pageSize);
  return { ...bounds, items: items.slice(bounds.offset, bounds.end) };
}

export function matchesSearch(values: unknown[], search: string): boolean {
  const normalize = (value: unknown) => String(value ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
  const term = normalize(search).trim();
  if (!term) return true;
  const phoneQuery = /^[+\d\s().-]+$/.test(term);
  const digits = term.replace(/\D/g, '');
  return values.some(value => normalize(value).includes(term)
    || phoneQuery && !!digits && String(value ?? '').replace(/\D/g, '').includes(digits));
}
