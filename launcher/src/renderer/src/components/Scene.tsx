import { useEffect, useRef, useState, type CSSProperties } from 'react';
import bat from '../../assets/bat.png';
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
 * covers the window closely, anchored to the bottom. Fog drifts, the lights flicker, a bat flies
 * by now and then and the layers follow the mouse a little; with motion off it is a still image.
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

  useEffect(() => {
    const el = ref.current;
    if (!el || !motion) {
      el?.style.setProperty('--mx', '0');
      el?.style.setProperty('--my', '0');
      return;
    }
    let frame = 0;
    const move = (e: MouseEvent) => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        el.style.setProperty('--mx', (e.clientX / window.innerWidth - 0.5).toFixed(3));
        el.style.setProperty('--my', (e.clientY / window.innerHeight - 0.5).toFixed(3));
      });
    };
    window.addEventListener('mousemove', move);
    return () => {
      window.removeEventListener('mousemove', move);
      cancelAnimationFrame(frame);
    };
  }, [motion]);

  const style = { '--s': scale, '--sw': `${W * scale}px`, '--sh': `${H * scale}px`, '--snow': `url(${snow})` } as CSSProperties;
  const layer = (src: string, depth: number, className = '') => (
    <div className={`scene__layer ${className}`} style={{ backgroundImage: `url(${src})`, '--depth': depth } as CSSProperties} />
  );

  return (
    <div ref={ref} className={`scene${dim ? ' scene--dim' : ''}${home ? ' scene--home' : ''}`} style={style} aria-hidden="true">
      <div className="scene__underground" />
      <div className="scene__frame">
        {layer(sky, 1)}
        {layer(far, 2)}
        <div className="scene__bat">
          <div className="scene__bat-wings" style={{ backgroundImage: `url(${bat})` }} />
        </div>
        {layer(forest, 3)}
        <div className="scene__fog" style={{ backgroundImage: `url(${fog})`, top: `calc(${FOG_Y}px * var(--s))`, height: `calc(${FOG_H}px * var(--s))` }} />
        {layer(ground, 5)}
        {layer(glow, 5, 'scene__glow')}
      </div>
      <div className="scene__snow scene__snow--far" />
      <div className="scene__snow scene__snow--near" />
      <div className="scene__veil" />
    </div>
  );
}
