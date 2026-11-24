import * as stylex from "@stylexjs/stylex";
import React, { useState, useRef, useEffect } from "react";
import {
  HStack,
  VStack,
  Layout,
  LayoutHeader,
  LayoutContent,
  LayoutFooter,
} from "@astryxdesign/core/Layout";
import { Text } from "@astryxdesign/core/Text";
import { Button } from "@astryxdesign/core/Button";
import { IconButton } from "@astryxdesign/core/IconButton";
import { Icon } from "@astryxdesign/core/Icon";
import { Slider } from "@astryxdesign/core/Slider";
import { TextInput } from "@astryxdesign/core/TextInput";
import { Theme } from "@astryxdesign/core/theme";
import { neutralTheme } from "@astryxdesign/theme-neutral/built";
import {
  ArrowLeft,
  ArrowDownToLine,
  Undo2,
  Redo2,
  History,
  Crop,
  SlidersHorizontal,
  Smile,
  Type,
  Paintbrush,
  Grid2X2,
  RotateCw,
  FlipHorizontal2,
  Sparkles,
  Check,
  Trash2,
  Eye,
  Plus,
  ChevronDown,
} from "lucide-react";
import "@astryxdesign/core/reset.css";
import "@astryxdesign/core/astryx.css";

// Adapted from Astryx canvas-editor: fixed document frame, header/content/footer,
// gap={2} toolbar rhythm and padding={3} inspector. Mobile inspector lives below canvas.
type Layer = {
  id: number;
  kind: "sticker" | "text";
  value: string;
  x: number;
  y: number;
  size: number;
  rotation: number;
  outline: boolean;
  background: boolean;
};
type Stroke = {
  points: number[][];
  mosaic: boolean;
};
type Edit = {
  filter: number;
  intensity: number;
  brightness: number;
  saturation: number;
  contrast: number;
  rotation: number;
  flip: boolean;
  crop: string;
  layers: Layer[];
  strokes: Stroke[];
};
const INITIAL: Edit = {
  filter: 0,
  intensity: 75,
  brightness: 0,
  saturation: 0,
  contrast: 0,
  rotation: 0,
  flip: false,
  crop: "原图",
  layers: [],
  strokes: [],
};
const FILTERS = [
  {
    name: "原图",
    group: "全部",
    css: "",
  },
  {
    name: "奶油",
    group: "日常",
    css: "sepia(.2) saturate(.8) brightness(1.08)",
  },
  {
    name: "冷白",
    group: "日常",
    css: "saturate(.7) hue-rotate(12deg) brightness(1.1)",
  },
  {
    name: "复古",
    group: "胶片",
    css: "sepia(.5) contrast(.9)",
  },
  {
    name: "暗调",
    group: "电影",
    css: "contrast(1.3) brightness(.85)",
  },
  {
    name: "通透",
    group: "日常",
    css: "saturate(1.2) brightness(1.08)",
  },
  {
    name: "黑白",
    group: "胶片",
    css: "grayscale(1)",
  },
  {
    name: "暖阳",
    group: "日常",
    css: "sepia(.35) saturate(1.4)",
  },
  {
    name: "青橙",
    group: "电影",
    css: "hue-rotate(335deg) saturate(1.5)",
  },
  {
    name: "灰阶",
    group: "胶片",
    css: "grayscale(.8) contrast(.85)",
  },
  {
    name: "暮色",
    group: "电影",
    css: "sepia(.3) hue-rotate(320deg) brightness(.9)",
  },
];
const tools = [
  {
    name: "滤镜",
    icon: Sparkles,
  },
  {
    name: "调整",
    icon: SlidersHorizontal,
  },
  {
    name: "贴纸",
    icon: Smile,
  },
  {
    name: "文字",
    icon: Type,
  },
  {
    name: "裁剪",
    icon: Crop,
  },
  {
    name: "涂鸦",
    icon: Paintbrush,
  },
  {
    name: "马赛克",
    icon: Grid2X2,
  },
  {
    name: "旋转",
    icon: RotateCw,
  },
  {
    name: "镜像",
    icon: FlipHorizontal2,
  },
];
const ink = "var(--color-text-primary)";
const muted = "var(--color-text-secondary)";
const surface = "var(--color-background-surface)";
const space = "var(--spacing-3)";
function I({ icon }: { icon: any }) {
  return <Icon icon={icon} size="sm" />;
}
function Action({
  label,
  icon,
  onClick,
  disabled = false,
}: {
  label: string;
  icon: any;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <IconButton
      label={label}
      tooltip={label}
      icon={<I icon={icon} />}
      variant="ghost"
      isDisabled={disabled}
      onClick={onClick}
    />
  );
}

// Original procedural artwork. These colors belong to the image, not the UI theme.
function drawCoast(ctx: CanvasRenderingContext2D, w: number, h: number) {
  ctx.save();
  ctx.scale(w / 900, h / 1200);
  const sky = ctx.createLinearGradient(0, 0, 0, 620);
  sky.addColorStop(0, "#92b7be");
  sky.addColorStop(0.75, "#dce0cf");
  sky.addColorStop(1, "#f3ddbb");
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, 900, 1200);
  ctx.fillStyle = "#faf0d1";
  ctx.beginPath();
  ctx.arc(662, 267, 65, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#7faaa7";
  ctx.beginPath();
  ctx.moveTo(0, 580);
  ctx.bezierCurveTo(180, 470, 300, 535, 480, 510);
  ctx.bezierCurveTo(630, 480, 720, 540, 900, 485);
  ctx.lineTo(900, 790);
  ctx.lineTo(0, 790);
  ctx.fill();
  const sea = ctx.createLinearGradient(0, 600, 900, 1200);
  sea.addColorStop(0, "#5e999c");
  sea.addColorStop(0.5, "#397e82");
  sea.addColorStop(1, "#9bc1b4");
  ctx.fillStyle = sea;
  ctx.fillRect(0, 625, 900, 575);
  ctx.fillStyle = "#c7b494";
  ctx.beginPath();
  ctx.moveTo(0, 525);
  ctx.bezierCurveTo(100, 540, 110, 670, 235, 720);
  ctx.bezierCurveTo(390, 800, 415, 830, 485, 910);
  ctx.bezierCurveTo(510, 960, 460, 1010, 660, 1200);
  ctx.lineTo(0, 1200);
  ctx.fill();
  ctx.fillStyle = "#727b5b";
  ctx.beginPath();
  ctx.moveTo(0, 500);
  ctx.bezierCurveTo(120, 550, 90, 650, 215, 705);
  ctx.bezierCurveTo(330, 770, 300, 870, 420, 895);
  ctx.bezierCurveTo(350, 970, 390, 1060, 490, 1200);
  ctx.lineTo(0, 1200);
  ctx.fill();
  ctx.strokeStyle = "#e4e2c5";
  ctx.lineWidth = 8;
  ctx.beginPath();
  ctx.moveTo(18, 585);
  ctx.bezierCurveTo(150, 675, 100, 700, 270, 790);
  ctx.bezierCurveTo(390, 850, 300, 940, 470, 1150);
  ctx.stroke();
  for (let i = 0; i < 95; i++) {
    const x = 460 + (Math.sin(i * 81.32) * 0.5 + 0.5) * 440,
      y = 650 + i * 6;
    ctx.strokeStyle = `rgba(240,244,223,${0.04 + (i % 5) * 0.02})`;
    ctx.lineWidth = 1 + (i % 3);
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x + 10 + (i % 37), y - 2);
    ctx.stroke();
  }
  let seed = 7;
  for (let i = 0; i < 22000; i++) {
    seed = (seed * 16807) % 2147483647;
    const x = seed % 900;
    seed = (seed * 16807) % 2147483647;
    const y = seed % 1200;
    ctx.fillStyle = i % 2 ? "rgba(255,246,215,.035)" : "rgba(0,30,30,.025)";
    ctx.fillRect(x, y, 2, 2);
  }
  ctx.restore();
}
export default function App() {
  const [timeline, setTimeline] = useState([
    {
      label: "原始图片",
      edit: INITIAL,
    },
  ]);
  const [cursor, setCursor] = useState(0);
  const [draft, setDraft] = useState<Edit | null>(null);
  const edit = draft ?? timeline[cursor].edit;
  const [tool, setTool] = useState("滤镜");
  const [panel, setPanel] = useState(true);
  const [full, setFull] = useState(false);
  const [category, setCategory] = useState("全部");
  const [stickerTab, setStickerTab] = useState("贴纸");
  const [selected, setSelected] = useState<number | null>(null);
  const [compare, setCompare] = useState(false);
  const [notice, setNotice] = useState("");
  const [text, setText] = useState("去有风的地方");
  const canvas = useRef<HTMLCanvasElement>(null);
  const art = useRef<HTMLCanvasElement | null>(null);
  const drag = useRef<any>(null);
  const [drawing, setDrawing] = useState<number[][] | null>(null);
  function commit(next: Edit, label: string) {
    setTimeline((t) => [
      ...t.slice(0, cursor + 1),
      {
        label,
        edit: structuredClone(next),
      },
    ]);
    setCursor(cursor + 1);
    setDraft(null);
  }
  function patch(p: Partial<Edit>, label: string) {
    commit(
      {
        ...edit,
        ...p,
      },
      label,
    );
  }
  function moveHistory(n: number) {
    setDraft(null);
    setCursor(Math.max(0, Math.min(timeline.length - 1, n)));
    setSelected(null);
  }
  function open(name: string) {
    setDraft(null);
    if (name === "旋转") {
      patch(
        {
          rotation: (edit.rotation + 90) % 360,
        },
        "旋转 90°",
      );
      return;
    }
    if (name === "镜像") {
      patch(
        {
          flip: !edit.flip,
        },
        "水平镜像",
      );
      return;
    }
    setTool(name);
    setPanel(true);
    setFull(name === "裁剪" || name === "历史");
  }
  function addLayer(kind: "sticker" | "text", value: string) {
    const l: Layer = {
      id: Date.now(),
      kind,
      value,
      x: 0.5,
      y: 0.47,
      size: kind === "text" ? 48 : 100,
      rotation: 0,
      outline: false,
      background: false,
    };
    patch(
      {
        layers: [...edit.layers, l],
      },
      kind === "text" ? "添加文字" : "添加贴纸",
    );
    setSelected(l.id);
  }
  const layer = edit.layers.find((l) => l.id === selected);
  function updateLayer(p: Partial<Layer>, label: string) {
    patch(
      {
        layers: edit.layers.map((l) =>
          l.id === selected
            ? {
                ...l,
                ...p,
              }
            : l,
        ),
      },
      label,
    );
  }
  function paint(
    target: HTMLCanvasElement,
    original = false,
    selection = true,
  ) {
    const ctx = target.getContext("2d")!;
    const e = original ? INITIAL : edit;
    if (!art.current) {
      art.current = document.createElement("canvas");
      art.current.width = 900;
      art.current.height = 1200;
      drawCoast(art.current.getContext("2d")!, 900, 1200);
    }
    ctx.clearRect(0, 0, 900, 1200);
    ctx.save();
    ctx.translate(450, 600);
    ctx.rotate((e.rotation * Math.PI) / 180);
    ctx.scale(e.flip ? -1 : 1, 1);
    if (e.rotation % 180 !== 0) ctx.scale(0.75, 0.75);
    ctx.filter = `brightness(${1 + e.brightness / 160}) saturate(${1 + e.saturation / 100}) contrast(${1 + e.contrast / 160})`;
    ctx.drawImage(art.current, -450, -600);
    ctx.restore();
    if (e.filter) {
      const overlay = document.createElement("canvas");
      overlay.width = 900;
      overlay.height = 1200;
      const c = overlay.getContext("2d")!;
      c.filter = FILTERS[e.filter].css;
      c.drawImage(target, 0, 0);
      ctx.globalAlpha = e.intensity / 100;
      ctx.drawImage(overlay, 0, 0);
      ctx.globalAlpha = 1;
    }
    for (const s of [
      ...e.strokes,
      ...(drawing
        ? [
            {
              points: drawing,
              mosaic: tool === "马赛克",
            },
          ]
        : []),
    ]) {
      if (s.mosaic) {
        for (const p of s.points) {
          for (let k = 0; k < 9; k++) {
            ctx.fillStyle = ["#799b93", "#b1bbaa", "#c5bea2", "#608a85"][k % 4];
            ctx.fillRect(
              p[0] * 900 + ((k % 3) - 1) * 18,
              p[1] * 1200 + (Math.floor(k / 3) - 1) * 18,
              20,
              20,
            );
          }
        }
      } else {
        ctx.beginPath();
        ctx.strokeStyle = "#faf0d1";
        ctx.lineWidth = 10;
        ctx.lineCap = "round";
        ctx.lineJoin = "round";
        s.points.forEach((p, i) =>
          i
            ? ctx.lineTo(p[0] * 900, p[1] * 1200)
            : ctx.moveTo(p[0] * 900, p[1] * 1200),
        );
        ctx.stroke();
      }
    }
    for (const l of e.layers) {
      ctx.save();
      ctx.translate(l.x * 900, l.y * 1200);
      ctx.rotate((l.rotation * Math.PI) / 180);
      ctx.font = `${l.kind === "text" ? "600 " : ""}${l.size}px system-ui`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      if (l.background) {
        const width = ctx.measureText(l.value).width;
        ctx.fillStyle = "#faf0d1";
        ctx.fillRect(
          -width / 2 - 18,
          -l.size / 2 - 12,
          width + 36,
          l.size + 24,
        );
      }
      ctx.fillStyle = l.background ? "#284f50" : "#fff6df";
      if (l.outline) {
        ctx.lineWidth = 6;
        ctx.strokeStyle = "#284f50";
        ctx.strokeText(l.value, 0, 0);
      }
      ctx.fillText(l.value, 0, 0);
      if (l.id === selected && !original && selection) {
        ctx.strokeStyle = "#fff6df";
        ctx.lineWidth = 2;
        ctx.setLineDash([8, 6]);
        const width = ctx.measureText(l.value).width;
        ctx.strokeRect(
          -width / 2 - 16,
          -l.size * 0.65,
          width + 32,
          l.size * 1.3,
        );
      }
      ctx.restore();
    }
  }
  useEffect(() => {
    if (canvas.current) paint(canvas.current, compare);
  }, [edit, compare, selected, drawing]);
  useEffect(() => {
    if (!notice) return;
    const id = setTimeout(() => setNotice(""), 3500);
    return () => clearTimeout(id);
  }, [notice]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement) return;
      if ((e.metaKey || e.ctrlKey) && e.key === "z") {
        e.preventDefault();
        moveHistory(cursor + (e.shiftKey ? 1 : -1));
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [cursor, timeline]);
  function point(e: React.PointerEvent<HTMLCanvasElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    return [(e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height];
  }
  function pointerDown(e: React.PointerEvent<HTMLCanvasElement>) {
    if (compare) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    const p = point(e);
    if (tool === "涂鸦" || tool === "马赛克") {
      setDrawing([p]);
      return;
    }
    const hit = [...edit.layers]
      .reverse()
      .find(
        (l) =>
          Math.abs(l.x - p[0]) <
            Math.max(0.08, (l.value.length * l.size) / 1800) &&
          Math.abs(l.y - p[1]) < l.size / 1200,
      );
    setSelected(hit?.id ?? null);
    if (hit)
      drag.current = {
        id: hit.id,
        start: p,
        initial: edit,
        layer: hit,
      };
  }
  function pointerMove(e: React.PointerEvent<HTMLCanvasElement>) {
    const p = point(e);
    if (drawing) {
      setDrawing([...drawing, p]);
      return;
    }
    if (drag.current) {
      const d = drag.current;
      setDraft({
        ...d.initial,
        layers: d.initial.layers.map((l: Layer) =>
          l.id === d.id
            ? {
                ...l,
                x: Math.max(
                  0.05,
                  Math.min(0.95, d.layer.x + p[0] - d.start[0]),
                ),
                y: Math.max(
                  0.05,
                  Math.min(0.95, d.layer.y + p[1] - d.start[1]),
                ),
              }
            : l,
        ),
      });
    }
  }
  function pointerUp() {
    if (drawing) {
      patch(
        {
          strokes: [
            ...edit.strokes,
            {
              points: drawing,
              mosaic: tool === "马赛克",
            },
          ],
        },
        tool === "马赛克" ? "马赛克笔迹" : "涂鸦笔迹",
      );
      setDrawing(null);
    }
    if (drag.current) {
      if (draft) commit(draft, "移动图层");
      drag.current = null;
    }
  }
  function save() {
    const out = document.createElement("canvas");
    out.width = 900;
    out.height = 1200;
    const old = selected;
    setSelected(null);
    paint(out, false, false);
    let exportCanvas = out;
    if (edit.crop !== "原图") {
      const [a, b] = edit.crop.split(":").map(Number);
      const ratio = a / b;
      const w = Math.min(900, 1200 * ratio),
        h = w / ratio;
      exportCanvas = document.createElement("canvas");
      exportCanvas.width = w;
      exportCanvas.height = h;
      exportCanvas
        .getContext("2d")!
        .drawImage(out, (900 - w) / 2, (1200 - h) / 2, w, h, 0, 0, w, h);
    }
    const link = document.createElement("a");
    link.download = "留白-海岸.png";
    link.href = exportCanvas.toDataURL("image/png");
    link.click();
    setSelected(old);
    setNotice("图片已导出 · PNG");
  }
  const chip = (label: string, active: boolean, onClick: () => void) => (
    <Button
      key={label}
      label={label}
      size="sm"
      variant={active ? "primary" : "ghost"}
      onClick={onClick}
    />
  );
  const slider = (
    label: string,
    key: "intensity" | "brightness" | "contrast" | "saturation",
    min = 0,
    max = 100,
  ) => (
    <Slider
      key={key}
      label={label}
      value={edit[key]}
      min={min}
      max={max}
      valueDisplay="text"
      formatValue={(n) => `${n}${key === "intensity" ? "%" : ""}`}
      onChange={(n: number) =>
        setDraft({
          ...edit,
          [key]: n,
        })
      }
      onChangeEnd={(n: number) =>
        commit(
          {
            ...edit,
            [key]: n,
          },
          `调整${label}`,
        )
      }
    />
  );
  return (
    <Theme theme={neutralTheme} mode="dark">
      <VStack gap={0} xstyle={styles.region1}>
        <VStack gap={0} xstyle={styles.region2}>
          <Layout
            height="fill"
            header={
              <LayoutHeader padding={3} hasDivider={false}>
                <HStack gap={2} hAlign="between">
                  <HStack gap={2}>
                    <Action
                      label="收起工具"
                      icon={ArrowLeft}
                      onClick={() => {
                        setPanel(false);
                        setFull(false);
                      }}
                    />
                    <VStack gap={1}>
                      <Text weight="semibold">留白</Text>
                      <Text type="supporting" color="secondary">
                        海岸日记 · 本地创作
                      </Text>
                    </VStack>
                  </HStack>
                  <Button
                    label="导出"
                    size="sm"
                    icon={<I icon={ArrowDownToLine} />}
                    onClick={save}
                  />
                </HStack>
              </LayoutHeader>
            }
            content={
              <LayoutContent padding={0}>
                <VStack gap={0} xstyle={styles.region3}>
                  <HStack paddingInline={3} paddingBlock={1} hAlign="between">
                    <HStack gap={0}>
                      <Action
                        label="撤销"
                        icon={Undo2}
                        disabled={cursor === 0}
                        onClick={() => moveHistory(cursor - 1)}
                      />
                      <Action
                        label="重做"
                        icon={Redo2}
                        disabled={cursor === timeline.length - 1}
                        onClick={() => moveHistory(cursor + 1)}
                      />
                      <Action
                        label="编辑历史"
                        icon={History}
                        onClick={() => open("历史")}
                      />
                    </HStack>
                    <Text type="supporting" color="secondary">
                      {compare ? "原始图片" : `${edit.crop} · 900 × 1200`}
                    </Text>
                    <HStack
                      as="button"
                      gap={1}
                      padding={2}
                      xstyle={styles.region4}
                      aria-label="按住对比原图"
                      onPointerDown={() => setCompare(true)}
                      onPointerUp={() => setCompare(false)}
                      onPointerLeave={() => setCompare(false)}
                      onPointerCancel={() => setCompare(false)}
                      onKeyDown={(e: any) => {
                        if (e.key === " " || e.key === "Enter") {
                          e.preventDefault();
                          setCompare(true);
                        }
                      }}
                      onKeyUp={() => setCompare(false)}
                    >
                      <I icon={Eye} />
                      <Text type="supporting">对比</Text>
                    </HStack>
                  </HStack>
                  <VStack gap={2} padding={3} xstyle={styles.region5}>
                    <VStack gap={0} xstyle={styles.region6}>
                      <canvas
                        ref={canvas}
                        width={900}
                        height={1200}
                        aria-label="可编辑海岸画布，拖动选中贴纸或文字"
                        onPointerDown={pointerDown}
                        onPointerMove={pointerMove}
                        onPointerUp={pointerUp}
                        onPointerCancel={pointerUp}
                        {...stylex.props(
                          styles.region7(
                            tool === "涂鸦" || tool === "马赛克"
                              ? "crosshair"
                              : "grab",
                          ),
                        )}
                      />
                      {(tool === "裁剪" || edit.crop !== "原图") && (
                        <VStack
                          gap={0}
                          xstyle={styles.region8(
                            edit.crop === "原图"
                              ? "0"
                              : edit.crop === "1:1"
                                ? "12.5% 0"
                                : edit.crop === "16:9"
                                  ? "28.90625% 0"
                                  : edit.crop === "9:16"
                                    ? "0 12.5%"
                                    : "21.875% 0",
                          )}
                        >
                          {tool === "裁剪" && (
                            <svg
                              width="100%"
                              height="100%"
                              viewBox="0 0 100 100"
                              preserveAspectRatio="none"
                              aria-label="裁剪九宫格"
                            >
                              <path
                                d="M 33 0 V 100 M 66 0 V 100 M 0 33 H 100 M 0 66 H 100"
                                vectorEffect="non-scaling-stroke"
                                stroke="currentColor"
                                opacity=".4"
                                fill="none"
                              />
                            </svg>
                          )}
                        </VStack>
                      )}
                    </VStack>
                    {!full && (
                      <Text type="supporting" color="secondary">
                        {layer
                          ? "拖动调整位置 · 下方编辑图层"
                          : tool === "涂鸦"
                            ? "在画面上自由绘制"
                            : tool === "马赛克"
                              ? "马赛克效果演示"
                              : "让每一张照片，都有自己的情绪"}
                      </Text>
                    )}
                  </VStack>
                  {notice && (
                    <HStack
                      padding={3}
                      hAlign="center"
                      xstyle={styles.region9}
                      role="status"
                    >
                      <I icon={Check} />
                      <Text>{notice}</Text>
                    </HStack>
                  )}
                </VStack>
              </LayoutContent>
            }
            footer={
              <LayoutFooter padding={0} hasDivider>
                {panel && (
                  <VStack
                    gap={3}
                    padding={3}
                    xstyle={styles.region10(
                      tool === "历史" ? "100%" : full ? "42dvh" : "36dvh",
                      tool === "历史" ? "absolute" : "static",
                      tool === "历史" ? 0 : "auto",
                      tool === "历史" ? 10 : "auto",
                    )}
                  >
                    <HStack hAlign="between" gap={2}>
                      <HStack gap={2}>
                        <Text weight="semibold">
                          {tool === "历史"
                            ? "编辑历史"
                            : tool === "滤镜"
                              ? "为此刻，选一种色调"
                              : tool === "贴纸"
                                ? "贴一点好心情"
                                : tool === "文字"
                                  ? "写下这一刻"
                                  : tool === "裁剪"
                                    ? "构图，恰到好处"
                                    : tool}
                        </Text>
                        {tool === "滤镜" && (
                          <Text type="supporting" color="secondary">
                            11 款
                          </Text>
                        )}
                      </HStack>
                      <Action
                        label="收起面板"
                        icon={ChevronDown}
                        onClick={() => {
                          setPanel(false);
                          setFull(false);
                        }}
                      />
                    </HStack>
                    {tool === "滤镜" && (
                      <>
                        <HStack gap={1}>
                          {["全部", "日常", "胶片", "电影"].map((c) =>
                            chip(c, c === category, () => setCategory(c)),
                          )}
                        </HStack>
                        <HStack gap={2} isScrollable xstyle={styles.region11}>
                          {FILTERS.map(
                            (f, i) =>
                              (category === "全部" || f.group === category) && (
                                <VStack
                                  key={f.name}
                                  gap={2}
                                  as="button"
                                  aria-label={`滤镜 ${f.name}`}
                                  aria-pressed={edit.filter === i}
                                  onClick={() =>
                                    patch(
                                      {
                                        filter: i,
                                      },
                                      `滤镜 · ${f.name}`,
                                    )
                                  }
                                  xstyle={styles.region12}
                                >
                                  <VStack
                                    gap={0}
                                    xstyle={styles.region13(
                                      edit.filter === i ? ink : "transparent",
                                    )}
                                  >
                                    <MiniArt filter={f.css} />
                                    {edit.filter === i && (
                                      <HStack xstyle={styles.region14}>
                                        <Icon icon={Check} size="xsm" />
                                      </HStack>
                                    )}
                                  </VStack>
                                  <Text
                                    type="supporting"
                                    weight={
                                      edit.filter === i ? "bold" : "normal"
                                    }
                                  >
                                    {f.name}
                                  </Text>
                                </VStack>
                              ),
                          )}
                        </HStack>
                        {slider("强度", "intensity")}
                      </>
                    )}
                    {tool === "调整" && (
                      <>
                        {slider("亮度", "brightness", -100)}
                        {slider("对比度", "contrast", -100)}
                        {slider("饱和度", "saturation", -100)}
                      </>
                    )}
                    {tool === "贴纸" && (
                      <>
                        <HStack gap={1}>
                          {["贴纸", "Emoji"].map((t) =>
                            chip(t, t === stickerTab, () => setStickerTab(t)),
                          )}
                        </HStack>
                        <HStack gap={2} wrap="wrap">
                          {(stickerTab === "贴纸"
                            ? ["✦", "✿", "☀", "♡", "☁", "〰", "☾", "✧"]
                            : ["🌻", "🍋", "🐈", "🌊", "🫧", "🦋", "🌈", "☕"]
                          ).map((s) => (
                            <Button
                              key={s}
                              label={s}
                              size="lg"
                              variant="secondary"
                              onClick={() => addLayer("sticker", s)}
                            />
                          ))}
                        </HStack>
                        {!layer && (
                          <Text type="supporting" color="secondary">
                            点击添加，拖动定位。大小和角度可精细调整。
                          </Text>
                        )}
                      </>
                    )}
                    {tool === "文字" && (
                      <>
                        <TextInput
                          label="文字内容"
                          isLabelHidden
                          placeholder="写下这一刻…"
                          value={layer?.kind === "text" ? layer.value : text}
                          onChange={(v) => {
                            if (layer?.kind === "text")
                              setDraft({
                                ...edit,
                                layers: edit.layers.map((l) =>
                                  l.id === selected
                                    ? {
                                        ...l,
                                        value: v,
                                      }
                                    : l,
                                ),
                              });
                            else setText(v);
                          }}
                          onEnter={() => {
                            if (layer?.kind === "text")
                              commit(edit, "编辑文字");
                            else if (text.trim()) addLayer("text", text);
                          }}
                        />
                        <HStack gap={2}>
                          <Button
                            label={
                              layer?.kind === "text" ? "保存文字" : "添加文字"
                            }
                            size="sm"
                            icon={<I icon={Plus} />}
                            onClick={() =>
                              layer?.kind === "text"
                                ? commit(edit, "编辑文字")
                                : text.trim() && addLayer("text", text)
                            }
                          />
                          {layer?.kind === "text" && (
                            <>
                              {chip("描边", layer.outline, () =>
                                updateLayer(
                                  {
                                    outline: !layer.outline,
                                  },
                                  "文字描边",
                                ),
                              )}
                              {chip("背景", layer.background, () =>
                                updateLayer(
                                  {
                                    background: !layer.background,
                                  },
                                  "文字背景",
                                ),
                              )}
                            </>
                          )}
                        </HStack>
                      </>
                    )}
                    {layer && (tool === "贴纸" || tool === "文字") && (
                      <>
                        <HStack hAlign="between">
                          <Text type="supporting" color="secondary">
                            已选中 ·{" "}
                            {layer.kind === "text" ? "文字" : layer.value}
                          </Text>
                          <Action
                            label="删除图层"
                            icon={Trash2}
                            onClick={() => {
                              patch(
                                {
                                  layers: edit.layers.filter(
                                    (l) => l.id !== selected,
                                  ),
                                },
                                "删除图层",
                              );
                              setSelected(null);
                            }}
                          />
                        </HStack>
                        <Slider
                          label="大小"
                          min={24}
                          max={180}
                          value={layer.size}
                          valueDisplay="text"
                          onChange={(n: number) =>
                            setDraft({
                              ...edit,
                              layers: edit.layers.map((l) =>
                                l.id === selected
                                  ? {
                                      ...l,
                                      size: n,
                                    }
                                  : l,
                              ),
                            })
                          }
                          onChangeEnd={() => commit(edit, "调整图层大小")}
                        />
                        <Slider
                          label="旋转角度"
                          min={-180}
                          max={180}
                          value={layer.rotation}
                          valueDisplay="text"
                          formatValue={(n) => `${n}°`}
                          onChange={(n: number) =>
                            setDraft({
                              ...edit,
                              layers: edit.layers.map((l) =>
                                l.id === selected
                                  ? {
                                      ...l,
                                      rotation: n,
                                    }
                                  : l,
                              ),
                            })
                          }
                          onChangeEnd={() => commit(edit, "旋转图层")}
                        />
                      </>
                    )}
                    {tool === "裁剪" && (
                      <>
                        <HStack gap={1} isScrollable>
                          {["原图", "1:1", "4:3", "16:9", "9:16"].map((c) =>
                            chip(c, c === edit.crop, () =>
                              setDraft({
                                ...edit,
                                crop: c,
                              }),
                            ),
                          )}
                        </HStack>
                        <HStack gap={2} hAlign="between">
                          <Button
                            label="取消"
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setDraft(null);
                              setFull(false);
                              setPanel(false);
                            }}
                          />
                          <Action
                            label="旋转裁剪"
                            icon={RotateCw}
                            onClick={() =>
                              setDraft({
                                ...edit,
                                rotation: (edit.rotation + 90) % 360,
                              })
                            }
                          />
                          <Button
                            label="完成裁剪"
                            size="sm"
                            icon={<I icon={Check} />}
                            onClick={() => {
                              commit(edit, `裁剪 · ${edit.crop}`);
                              setPanel(false);
                              setFull(false);
                            }}
                          />
                        </HStack>
                        <Text type="supporting" color="secondary">
                          居中比例裁剪 · 导出时生效
                        </Text>
                      </>
                    )}
                    {tool === "历史" && (
                      <>
                        <VStack gap={1}>
                          {timeline.map((t, i) => (
                            <HStack
                              key={i}
                              as="button"
                              padding={2}
                              gap={3}
                              onClick={() => moveHistory(i)}
                              xstyle={styles.region15(
                                i === cursor
                                  ? "var(--color-background-body)"
                                  : "transparent",
                                i === cursor ? ink : muted,
                              )}
                            >
                              <Text type="supporting">
                                {String(i).padStart(2, "0")}
                              </Text>
                              <Text>{t.label}</Text>
                              {i === cursor && <I icon={Check} />}
                            </HStack>
                          ))}
                        </VStack>
                        <Button
                          label="还原到原图"
                          variant="secondary"
                          size="sm"
                          onClick={() => commit(INITIAL, "还原原图")}
                        />
                      </>
                    )}
                    {(tool === "涂鸦" || tool === "马赛克") && (
                      <HStack gap={2}>
                        <I icon={tool === "涂鸦" ? Paintbrush : Grid2X2} />
                        <Text color="secondary">
                          {tool === "涂鸦"
                            ? "奶白画笔 · 直接在画布上绘制"
                            : "示意效果，请勿用于隐私遮挡"}
                        </Text>
                      </HStack>
                    )}
                  </VStack>
                )}
                {!full && (
                  <HStack
                    gap={0}
                    paddingBlock={2}
                    paddingInline={1}
                    isScrollable
                    xstyle={styles.region16}
                  >
                    {tools.map((t) => (
                      <VStack
                        key={t.name}
                        as="button"
                        gap={2}
                        padding={2}
                        aria-label={`工具 ${t.name}`}
                        aria-pressed={tool === t.name && panel}
                        onClick={() => open(t.name)}
                        xstyle={styles.region17(
                          tool === t.name && panel ? surface : "transparent",
                          tool === t.name && panel ? ink : muted,
                        )}
                      >
                        <I icon={t.icon} />
                        <Text type="supporting" textWrap="nowrap">
                          {t.name}
                        </Text>
                      </VStack>
                    ))}
                  </HStack>
                )}
              </LayoutFooter>
            }
          />
        </VStack>
      </VStack>
    </Theme>
  );
}
function MiniArt({ filter }: { filter: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    if (ref.current) drawCoast(ref.current.getContext("2d")!, 90, 120);
  }, []);
  return (
    <canvas
      ref={ref}
      width={90}
      height={120}
      aria-hidden="true"
      {...stylex.props(styles.region18(filter))}
    />
  );
}
const styles = stylex.create({
  region1: {
    backgroundColor: "var(--color-background-body)",
    color: "var(--color-text-primary)",
    minHeight: "100dvh",
    alignItems: "center",
  },
  region2: {
    width: "100%",
    maxWidth: "32rem",
    height: "100dvh",
    position: "relative",
    overflow: "hidden",
    borderInlineWidth: "0.0625rem",
    borderInlineStyle: "solid",
    borderInlineColor: "var(--color-border-default)",
  },
  region3: {
    height: "100%",
    position: "relative",
  },
  region4: {
    backgroundColor: "transparent",
    color: "var(--color-text-secondary)",
    borderWidth: 0,
    cursor: "pointer",
    borderRadius: "var(--radius-container)",
  },
  region5: {
    flex: 1,
    minHeight: 0,
    alignItems: "center",
    justifyContent: "center",
    position: "relative",
    backgroundColor: "var(--color-background-body)",
  },
  region6: {
    position: "relative",
    height: "100%",
    maxHeight: "45rem",
    maxWidth: "100%",
    aspectRatio: "3 / 4",
    overflow: "hidden",
    boxShadow:
      "0 1rem 3rem color-mix(in srgb, var(--color-text-primary) 5%, transparent)",
  },
  region7: (v0: any) => ({
    width: "100%",
    height: "100%",
    objectFit: "contain",
    touchAction: "none",
    cursor: v0,
  }),
  region8: (v0: any) => ({
    position: "absolute",
    inset: v0,
    borderWidth: "0.0625rem",
    borderStyle: "solid",
    borderColor: "var(--color-text-primary)",
    boxShadow:
      "0 0 0 30rem color-mix(in srgb, var(--color-background-body) 55%, transparent)",
    pointerEvents: "none",
  }),
  region9: {
    position: "absolute",
    top: "var(--spacing-3)",
    left: "var(--spacing-3)",
    right: "var(--spacing-3)",
    backgroundColor: "var(--color-background-surface)",
    borderRadius: "var(--radius-container)",
    zIndex: 5,
  },
  region10: (v0: any, v1: any, v2: any, v3: any) => ({
    backgroundColor: "var(--color-background-surface)",
    maxHeight: v0,
    overflowY: "auto",
    position: v1,
    inset: v2,
    zIndex: v3,
  }),
  region11: {
    flexShrink: 0,
    paddingBottom: "var(--spacing-1)",
  },
  region12: {
    flexShrink: 0,
    width: "4.25rem",
    padding: 0,
    borderWidth: 0,
    backgroundColor: "transparent",
    color: "var(--color-text-primary)",
    cursor: "pointer",
  },
  region13: (v0: any) => ({
    width: "4.25rem",
    height: "5rem",
    overflow: "hidden",
    borderRadius: "var(--radius-container)",
    borderWidth: "0.125rem",
    borderStyle: "solid",
    borderColor: v0,
    position: "relative",
  }),
  region14: {
    position: "absolute",
    right: "var(--spacing-1)",
    bottom: "var(--spacing-1)",
    borderRadius: "50%",
    backgroundColor: "var(--color-background-surface)",
    padding: "var(--spacing-1)",
  },
  region15: (v0: any, v1: any) => ({
    backgroundColor: v0,
    color: v1,
    borderWidth: 0,
    borderRadius: "var(--radius-container)",
    cursor: "pointer",
    textAlign: "start",
  }),
  region16: {
    backgroundColor: "var(--color-background-body)",
    paddingBottom: "max(var(--spacing-2), env(safe-area-inset-bottom))",
  },
  region17: (v0: any, v1: any) => ({
    minWidth: "3.6rem",
    flexShrink: 0,
    alignItems: "center",
    borderWidth: 0,
    borderRadius: "var(--radius-container)",
    backgroundColor: v0,
    color: v1,
    cursor: "pointer",
  }),
  region18: (v0: any) => ({
    width: "100%",
    height: "100%",
    objectFit: "cover",
    filter: v0,
  }),
});
