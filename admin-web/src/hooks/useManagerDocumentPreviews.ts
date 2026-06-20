import { useEffect, useState } from "react";

type UseManagerDocumentPreviewsParams<TManager, TKey extends string, TPreview> = {
  selectedManager: TManager | null;
  documentKeys: readonly TKey[];
  getManagerKey: (manager: TManager) => string;
  createIdleState: () => Record<TKey, TPreview>;
  createLoadingState: () => Record<TKey, TPreview>;
  createErrorPreview: (key: TKey) => TPreview;
  resolvePreview: (manager: TManager, key: TKey) => Promise<TPreview>;
};

export function useManagerDocumentPreviews<TManager, TKey extends string, TPreview>({
  selectedManager,
  documentKeys,
  getManagerKey,
  createIdleState,
  createLoadingState,
  createErrorPreview,
  resolvePreview,
}: UseManagerDocumentPreviewsParams<TManager, TKey, TPreview>) {
  const [loadedManagerKey, setLoadedManagerKey] = useState("");
  const [loadedPreviews, setLoadedPreviews] = useState<Record<TKey, TPreview>>(createIdleState);

  useEffect(() => {
    let cancelled = false;

    if (!selectedManager) {
      return () => {
        cancelled = true;
      };
    }
    const managerKey = getManagerKey(selectedManager);

    void Promise.allSettled(
      documentKeys.map(async (documentKey) => ({
        key: documentKey,
        preview: await resolvePreview(selectedManager, documentKey),
      })),
    ).then((results) => {
      if (cancelled) {
        return;
      }

      const nextState = createIdleState();
      results.forEach((result, index) => {
        const key = documentKeys[index];
        if (result.status === "fulfilled") {
          nextState[key] = result.value.preview;
          return;
        }
        nextState[key] = createErrorPreview(key);
      });
      setLoadedPreviews(nextState);
      setLoadedManagerKey(managerKey);
    }).catch(() => {
      if (cancelled) {
        return;
      }
      const nextState = createIdleState();
      documentKeys.forEach((key) => {
        nextState[key] = createErrorPreview(key);
      });
      setLoadedPreviews(nextState);
      setLoadedManagerKey(managerKey);
    });

    return () => {
      cancelled = true;
    };
  }, [
    createErrorPreview,
    createIdleState,
    createLoadingState,
    documentKeys,
    getManagerKey,
    resolvePreview,
    selectedManager,
  ]);

  if (!selectedManager) {
    return createIdleState();
  }
  if (loadedManagerKey !== getManagerKey(selectedManager)) {
    return createLoadingState();
  }
  return loadedPreviews;
}
