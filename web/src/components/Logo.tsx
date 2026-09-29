export function Logo({ size = 60 }: { size?: number }) {
  return (
    <p className="logo" style={{ fontSize: size }} aria-label="毎日">
      毎日
    </p>
  );
}

export function Splash() {
  return (
    <div className="splash" aria-busy="true">
      <p className="logo">毎日</p>
    </div>
  );
}
