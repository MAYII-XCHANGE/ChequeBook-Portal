import { useEffect, useState } from "react";

export function getLocalTimeGreeting(date = new Date()) {
  const hour = date.getHours();

  if (hour >= 5 && hour < 12) return "Good morning";
  if (hour >= 12 && hour < 17) return "Good afternoon";
  if (hour >= 17 && hour < 21) return "Good evening";
  return "Good night";
}

export function useLocalTimeGreeting() {
  const [greeting, setGreeting] = useState(() => getLocalTimeGreeting());

  useEffect(() => {
    const refreshGreeting = () => setGreeting(getLocalTimeGreeting());
    const intervalId = window.setInterval(refreshGreeting, 60_000);
    return () => window.clearInterval(intervalId);
  }, []);

  return greeting;
}
