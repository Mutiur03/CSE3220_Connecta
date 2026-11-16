import { buttonClasses, fieldClasses } from "@/components/ui/classes";

// Drawn with the same classes as LoginForm, so the form takes the same space when it arrives.
export function LoginFormFallback() {
  return (
    <div aria-hidden="true" className="flex w-full flex-col gap-4">
      {["Email", "Password"].map((label) => (
        <div key={label} className="flex flex-col gap-1">
          <span className="text-sm font-medium text-primary">{label}</span>
          <div className={fieldClasses}>&nbsp;</div>
        </div>
      ))}
      <div className="min-h-5" />
      <span className={buttonClasses("primary")}>Sign in</span>
    </div>
  );
}
