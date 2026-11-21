"use client";

import { useState, type ComponentProps } from "react";
import { Field } from "@/components/ui/field";

// A password input with a Show/Hide toggle. The input stays type=password for the browser's
// password manager until the person asks to see it.
export default function PasswordField(
  props: Omit<ComponentProps<typeof Field>, "type" | "trailing">,
) {
  const [visible, setVisible] = useState(false);

  return (
    <Field
      {...props}
      type={visible ? "text" : "password"}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          aria-pressed={visible}
          className="absolute inset-y-1 right-1 rounded-[0.375rem] px-2 text-caption text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary motion-reduce:transition-none"
        >
          {visible ? "Hide" : "Show"}
          <span className="sr-only"> password</span>
        </button>
      }
    />
  );
}
