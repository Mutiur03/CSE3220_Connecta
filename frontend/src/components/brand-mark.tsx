// Connecta brand art, drawn as masks so it takes the text colour and stays readable in both
// themes. BrandMark is the icon alone (public/brand/mark.png); BrandLogo is the full lockup with
// the name and tagline (public/brand/logo.png, ratio 1958:548).
function Mask({
  src,
  className,
  label,
}: {
  src: string;
  className: string;
  label?: string;
}) {
  const mask = `url(${src}) center / contain no-repeat`;
  return (
    <span
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      className={`inline-block shrink-0 bg-primary ${className}`}
      style={{ mask, WebkitMask: mask }}
    />
  );
}

/** The icon alone. Decorative: the product name sits beside it. */
export default function BrandMark({
  className = "size-7",
}: {
  className?: string;
}) {
  return <Mask src="/brand/mark.png" className={className} />;
}

/** The full logo. Give it a height; the width follows the artwork. */
export function BrandLogo({ className = "h-14" }: { className?: string }) {
  return (
    <Mask
      src="/brand/logo.png"
      label="Connecta"
      className={`aspect-[1958/548] ${className}`}
    />
  );
}
