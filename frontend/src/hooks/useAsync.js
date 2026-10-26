import { useCallback, useEffect, useState } from 'react';

export function useAsync(loader, dependencies = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const run = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try { const result = await loader(); setState({ data: result, loading: false, error: null }); }
    catch (error) { setState({ data: null, loading: false, error }); }
  }, dependencies); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { run(); }, [run]);
  return { ...state, retry: run };
}
