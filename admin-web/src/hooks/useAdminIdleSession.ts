import { useEffect } from "react";

const ADMIN_IDLE_TIMEOUT_MS = 15 * 60 * 1000;

export function useAdminIdleSession(enabled: boolean, onIdle: () => void) {
  useEffect(() => {
    if (!enabled) {
      return;
    }

    let timeoutId: number | null = null;

    const resetIdleTimer = () => {
      if (timeoutId !== null) {
        window.clearTimeout(timeoutId);
      }

      timeoutId = window.setTimeout(() => {
        onIdle();
      }, ADMIN_IDLE_TIMEOUT_MS);
    };

    const activityEvents: Array<keyof WindowEventMap> = [
      "click",
      "keydown",
      "mousemove",
      "scroll",
      "touchstart",
    ];

    activityEvents.forEach((eventName) => {
      window.addEventListener(eventName, resetIdleTimer, { passive: true });
    });
    resetIdleTimer();

    return () => {
      if (timeoutId !== null) {
        window.clearTimeout(timeoutId);
      }
      activityEvents.forEach((eventName) => {
        window.removeEventListener(eventName, resetIdleTimer);
      });
    };
  }, [enabled, onIdle]);
}
