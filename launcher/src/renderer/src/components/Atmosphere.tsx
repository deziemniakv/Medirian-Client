/**
 * Background of the home screen: violet moonlight, a crescent moon, faint stars, distant hills
 * and slowly drifting fog. Pure CSS/SVG — no images or canvas, negligible GPU cost.
 * The moon and warm horizon only appear in the Halloween theme.
 */
export function Atmosphere() {
  return (
    <div className="atmo" aria-hidden="true">
      <div className="atmo__glow" />
      <div className="atmo__stars" />
      <div className="atmo__moon" />
      <svg className="atmo__hills" viewBox="0 0 1200 220" preserveAspectRatio="none">
        <path className="atmo__hill atmo__hill--far" d="M0 150 C 140 110 260 120 380 135 S 620 95 760 118 S 1010 140 1200 105 V220 H0z" />
        <path className="atmo__hill atmo__hill--near" d="M0 185 C 180 160 300 175 450 168 S 700 150 860 172 S 1080 182 1200 160 V220 H0z" />
      </svg>
      <div className="atmo__fog atmo__fog--a" />
      <div className="atmo__fog atmo__fog--b" />
    </div>
  );
}
