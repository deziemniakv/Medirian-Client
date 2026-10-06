import { useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react';
import defaultSkin from '../../assets/default-skin.png';
import { useStore } from '../store';

type Face = 'front' | 'back' | 'right' | 'left' | 'top' | 'bottom';

/** Texture origin (u, v) of each part in the standard Minecraft skin layout, and its size. */
const PARTS = {
  head: { uv: [0, 0], overlay: [32, 0], size: [8, 8, 8] },
  body: { uv: [16, 16], overlay: [16, 32], size: [8, 12, 4] },
  rightArm: { uv: [40, 16], overlay: [40, 32], size: [4, 12, 4] },
  leftArm: { uv: [32, 48], overlay: [48, 48], size: [4, 12, 4] },
  rightLeg: { uv: [0, 16], overlay: [0, 32], size: [4, 12, 4] },
  leftLeg: { uv: [16, 48], overlay: [0, 48], size: [4, 12, 4] }
} as const;

/** Where each face of a w×h×d cuboid with texture origin (u, v) lies in the skin. */
function faceUv(face: Face, u: number, v: number, w: number, h: number, d: number): [number, number, number, number] {
  switch (face) {
    case 'top': return [u + d, v, w, d];
    case 'bottom': return [u + d + w, v, w, d];
    case 'right': return [u, v + d, d, h];
    case 'front': return [u + d, v + d, w, h];
    case 'left': return [u + d + w, v + d, d, h];
    default: return [u + 2 * d + w, v + d, w, h];
  }
}

/**
 * Prepares a skin exactly like Minecraft does before drawing it: old 64×32 skins become 64×64
 * (the left arm and leg are mirror copies of the right ones), an outer head layer without any
 * transparent pixel is dropped ("Notch's transparency hack") and the inner layers are made opaque.
 */
export function normalizeSkin(image: HTMLImageElement): string {
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const g = canvas.getContext('2d')!;
  g.imageSmoothingEnabled = false;
  g.drawImage(image, 0, 0);
  const legacy = image.naturalHeight === 32;
  const data = g.getImageData(0, 0, 64, 64);
  const px = data.data;
  const at = (x: number, y: number) => (y * 64 + x) * 4;
  if (legacy) {
    // [x, y, dx, dy, w, h]: copy right-limb faces into the left limbs, flipped horizontally
    const copies = [
      [4, 16, 16, 32, 4, 4], [8, 16, 16, 32, 4, 4], [0, 20, 24, 32, 4, 12], [4, 20, 16, 32, 4, 12], [8, 20, 8, 32, 4, 12],
      [12, 20, 16, 32, 4, 12], [44, 16, -8, 32, 4, 4], [48, 16, -8, 32, 4, 4], [40, 20, 0, 32, 4, 12], [44, 20, -8, 32, 4, 12],
      [48, 20, -16, 32, 4, 12], [52, 20, -8, 32, 4, 12]
    ];
    for (const [x, y, dx, dy, w, h] of copies) {
      for (let j = 0; j < h; j++) {
        for (let i = 0; i < w; i++) {
          const from = at(x + i, y + j);
          const to = at(x + dx + (w - 1 - i), y + dy + j);
          for (let c = 0; c < 4; c++) {
            px[to + c] = px[from + c];
          }
        }
      }
    }
    // an outer head layer with no transparency at all was never meant to be drawn
    let transparent = false;
    for (let y = 0; y < 16 && !transparent; y++) {
      for (let x = 32; x < 64; x++) {
        if (px[at(x, y) + 3] < 128) {
          transparent = true;
          break;
        }
      }
    }
    if (!transparent) {
      for (let y = 0; y < 16; y++) {
        for (let x = 32; x < 64; x++) {
          px[at(x, y) + 3] = 0;
        }
      }
    }
  }
  const opaque = (x1: number, y1: number, x2: number, y2: number) => {
    for (let y = y1; y < y2; y++) {
      for (let x = x1; x < x2; x++) {
        px[at(x, y) + 3] = 255;
      }
    }
  };
  opaque(0, 0, 32, 16);
  opaque(0, 16, 64, 32);
  opaque(16, 48, 48, 64);
  g.putImageData(data, 0, 0);
  return canvas.toDataURL('image/png');
}

const NORMALIZED = new Map<string, string>();

/** The current skin's texture (or Medirian's default), normalized to 64×64, and its model. */
export function useSkinTexture(): { src: string; slim: boolean; real: boolean } {
  const skin = useStore((s) => s.skin);
  const raw = skin?.texture ?? defaultSkin;
  const [src, setSrc] = useState(() => NORMALIZED.get(raw) ?? raw);
  useEffect(() => {
    const done = NORMALIZED.get(raw);
    if (done) {
      setSrc(done);
      return;
    }
    const image = new Image();
    image.onload = () => {
      const normalized = normalizeSkin(image);
      NORMALIZED.set(raw, normalized);
      setSrc(normalized);
    };
    image.src = raw;
  }, [raw]);
  return { src, slim: skin?.model === 'slim', real: !!skin?.texture };
}

/** The face of the skin (with the hat layer), drawn pixel-sharp at {@code size} px. */
export function SkinHead({ size = 26, className }: { size?: number; className?: string }) {
  const { src } = useSkinTexture();
  const s = size / 8;
  const layer = (u: number, v: number, scale = 1): CSSProperties => ({
    backgroundImage: `url(${src})`,
    backgroundSize: `${64 * s}px ${64 * s}px`,
    backgroundPosition: `${-u * s}px ${-v * s}px`,
    transform: scale !== 1 ? `scale(${scale})` : undefined
  });
  return (
    <span className={`skin-head${className ? ` ${className}` : ''}`} style={{ width: size, height: size }} aria-hidden="true">
      <span className="skin-head__layer" style={layer(8, 8)} />
      <span className="skin-head__layer" style={layer(40, 8, 1.125)} />
    </span>
  );
}

/** One cuboid of the model (a part or its outer layer), each face a slice of the skin. */
function Cuboid({ src, sheet, s, u, v, w, h, d, inflate = 0, mirror = false }: {
  src: string; sheet: number; s: number; u: number; v: number; w: number; h: number; d: number; inflate?: number; mirror?: boolean;
}) {
  const faces: Face[] = ['front', 'back', 'right', 'left', 'top', 'bottom'];
  const style = (face: Face): CSSProperties => {
    const [fu, fv, fw, fh] = faceUv(face, u, v, w, h, d);
    const base: CSSProperties = {
      width: fw * s,
      height: fh * s,
      backgroundImage: `url(${src})`,
      backgroundSize: `${64 * s}px ${sheet * s}px`,
      backgroundPosition: `${-fu * s}px ${-fv * s}px`
    };
    const half = (n: number) => (n * s) / 2;
    switch (face) {
      case 'front': return { ...base, transform: `translateZ(${half(d)}px)` };
      case 'back': return { ...base, transform: `rotateY(180deg) translateZ(${half(d)}px)` };
      case 'right': return { ...base, left: half(w - d), transform: `rotateY(-90deg) translateZ(${half(w)}px)` };
      case 'left': return { ...base, left: half(w - d), transform: `rotateY(90deg) translateZ(${half(w)}px)` };
      case 'top': return { ...base, top: half(h - d), transform: `rotateX(90deg) translateZ(${half(h)}px)` };
      default: return { ...base, top: half(h - d), transform: `rotateX(-90deg) translateZ(${half(h)}px) scaleY(-1)` };
    }
  };
  const scale = inflate ? `scale3d(${(w + inflate) / w}, ${(h + inflate) / h}, ${(d + inflate) / d})` : '';
  return (
    <div className="cuboid" style={{ width: w * s, height: h * s, transform: `${mirror ? 'scaleX(-1) ' : ''}${scale}` }}>
      {faces.map((face) => <div key={face} className="cuboid__face" style={style(face)} />)}
    </div>
  );
}

/**
 * The player in 3D from the real skin (CSS 3D, no WebGL): sways gently, follows a drag. Classic
 * and slim arms, and old 64×32 skins (mirrored limbs, no outer layer) are handled.
 */
export function Skin3D({ scale = 6, className, animate = true }: { scale?: number; className?: string; animate?: boolean }) {
  const { src, slim } = useSkinTexture();
  const sheet = 64;
  const [drag, setDrag] = useState<{ x: number; yaw: number } | null>(null);
  const [yaw, setYaw] = useState(-24);
  const frame = useRef(0);

  // a slow sway while nobody holds it
  useEffect(() => {
    if (!animate || drag) {
      return;
    }
    const start = performance.now();
    const base = yaw;
    const tick = (now: number) => {
      setYaw(base + Math.sin((now - start) / 2600) * 22);
      frame.current = requestAnimationFrame(tick);
    };
    frame.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [animate, drag]);

  const s = scale;
  const legacy = false;
  const armW = slim ? 3 : 4;
  const part = (name: keyof typeof PARTS, left: number, top: number) => {
    const p = PARTS[name];
    const [w, h, d] = name.endsWith('Arm') ? [armW, 12, 4] : p.size;
    // old skins have no left limbs: the right ones are mirrored
    const mirrorOf = legacy && name === 'leftArm' ? PARTS.rightArm : legacy && name === 'leftLeg' ? PARTS.rightLeg : null;
    const uv = mirrorOf ? mirrorOf.uv : p.uv;
    const outer = legacy ? (name === 'head' ? p.overlay : null) : p.overlay;
    return (
      <div className={`skin3d__part skin3d__part--${name}`} style={{ left: left * s, top: top * s, width: w * s, height: h * s }}>
        <Cuboid src={src} sheet={sheet} s={s} u={uv[0]} v={uv[1]} w={w} h={h} d={d} mirror={!!mirrorOf} />
        {outer && <Cuboid src={src} sheet={sheet} s={s} u={outer[0]} v={outer[1]} w={w} h={h} d={d} inflate={name === 'head' ? 1 : 0.5} />}
      </div>
    );
  };

  const onDown = (e: ReactPointerEvent) => {
    (e.target as Element).setPointerCapture(e.pointerId);
    setDrag({ x: e.clientX, yaw });
  };
  const onMove = (e: ReactPointerEvent) => {
    if (drag) {
      setYaw(drag.yaw + (e.clientX - drag.x) * 0.8);
    }
  };

  return (
    <div className={`skin3d${animate ? ' skin3d--alive' : ''}${className ? ` ${className}` : ''}`} style={{ width: 16 * s + 8 * s, height: 32 * s + 4 * s }}
      onPointerDown={onDown} onPointerMove={onMove} onPointerUp={() => setDrag(null)} onPointerCancel={() => setDrag(null)}>
      <div className="skin3d__model" style={{ width: 16 * s, height: 32 * s, transform: `rotateX(-8deg) rotateY(${yaw}deg)` }}>
        {part('head', 4, 0)}
        {part('body', 4, 8)}
        {part('rightArm', 4 - armW, 8)}
        {part('leftArm', 12, 8)}
        {part('rightLeg', 4, 20)}
        {part('leftLeg', 8, 20)}
      </div>
    </div>
  );
}
