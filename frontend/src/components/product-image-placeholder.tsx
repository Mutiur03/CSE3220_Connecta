import { ImageIcon } from "./icons";

type Size = "sm" | "lg";

const SIZE_CLASSES: Record<Size, string> = {
  sm: "size-10 rounded-control",
  lg: "size-24 rounded-panel",
};

/** Stands in for a product photo that does not exist yet: a neutral tile with an image icon. */
export default function ProductImagePlaceholder({ size = "sm" }: { size?: Size }) {
  return (
    <span
      role="img"
      aria-label="No image"
      className={`flex shrink-0 items-center justify-center border border-line bg-hover text-secondary ${SIZE_CLASSES[size]}`}
    >
      <ImageIcon />
    </span>
  );
}
