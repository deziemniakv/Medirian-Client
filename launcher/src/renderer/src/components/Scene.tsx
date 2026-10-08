import { useEffect, useRef, useState, type CSSProperties } from 'react';
import far from '../../assets/scene/far.png';
import fog from '../../assets/scene/fog.png';
import forest from '../../assets/scene/forest.png';
import glow from '../../assets/scene/glow.png';
import ground from '../../assets/scene/ground.png';
import sky from '../../assets/scene/sky.png';
import snow from '../../assets/snow.png';

/** Scene size in art pixels and where the fog band sits (scripts/pixel/scene.mjs). */
const W = 400;
const H = 250;
const FOG_H = 40;
const FOG_Y = 186;

/**
 * The Medirian night behind the launcher: the same pixel-art scene as the game's main menu
 * (scripts/generate-pixel-art.mjs). Drawn at whole screen pixels per art pixel whenever that
 * covers the window closely, anchored to the bottom. It is the backdrop, not the show: only the fog
 * drifts slowly and the windows glow; with motion off it is a still image.
 */
export function Scene({ dim, home, motion }: { dim: boolean; home: boolean; motion: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(3);

  useEffect(() => {
    const el = ref.current;
    if (!el) {
      return;
    }
    const fit = () => {
      const cover = Math.max(el.clientWidth / W, el.clientHeight / H);
      const whole = Math.ceil(cover - 0.001);
      setScale(whole / cover > 1.18 ? cover : whole);
    };
    fit();
    const observer = new ResizeObserver(fit);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const style = { '--s': scale, '--sw': `${W * scale}px`, '--sh': `${H * scale}px`, '--snow': `url(${snow})` } as CSSProperties;
  const layer = (src: string, className = '') => (
    <div className={`scene__layer ${className}`} style={{ backgroundImage: `url(${src})` }} />
  );

  return (
    <div ref={ref} className={`scene${dim ? ' scene--dim' : ''}${home ? ' scene--home' : ''}${motion ? '' : ' scene--still'}`} style={style} aria-hidden="true">
      <div className="scene__underground" />
      <div className="scene__frame">
        {layer(sky)}
        {layer(far)}
        {layer(forest)}
        <div className="scene__fog" style={{ backgroundImage: `url(${fog})`, top: `calc(${FOG_Y}px * var(--s))`, height: `calc(${FOG_H}px * var(--s))` }} />
        {layer(ground)}
        {layer(glow, 'scene__glow')}
      </div>
      <div className="scene__snow scene__snow--far" />
      <div className="scene__snow scene__snow--near" />
      <div className="scene__veil" />
    </div>
  );
}
