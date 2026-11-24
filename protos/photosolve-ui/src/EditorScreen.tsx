import { useState } from 'react';
import { VStack } from '@astryxdesign/core/VStack';
import { HStack } from '@astryxdesign/core/HStack';
import { Card } from '@astryxdesign/core/Card';
import { Section } from '@astryxdesign/core/Section';
import { Text } from '@astryxdesign/core/Text';
import { Heading } from '@astryxdesign/core/Heading';
import { Badge } from '@astryxdesign/core/Badge';
import { Divider } from '@astryxdesign/core/Divider';
import { Icon } from '@astryxdesign/core/Icon';
import { Button } from '@astryxdesign/core/Button';
import { IconButton } from '@astryxdesign/core/IconButton';
import { Slider } from '@astryxdesign/core/Slider';
import { Dialog, DialogHeader } from '@astryxdesign/core/Dialog';
import {
  PhotoIcon,
  CheckIcon,
  PlusIcon,
  XMarkIcon,
  ArrowUturnLeftIcon,
  SparklesIcon,
} from '@heroicons/react/24/outline';

/**
 * 图片编辑页设计稿（对应「编辑图片十分糟糕」整条抱怨）
 *
 * 四条设计约定，每条都对应用户截图里的一个具体问题：
 * 1. 画布 = 图片实际显示矩形，裁剪框/网格/变暗只画在这个矩形内（截图里网格压在黑底上、
 *    比照片大一整圈 → 框与图「不搭噶」）。
 * 2. 布局三段固定：顶栏（关闭/撤销/重做/完成）→ 画布 → 面板槽 → 工具条。
 *    工具条贴底且面板在工具条**上方**，标签永不被裁掉（截图里标签被底边切一半）。
 * 3. 颜色不是 6 个色块：16 色色板（Astryx swatch grid 规范：每行 8、一个 Tab 停靠点、
 *    aria-pressed）+ 「自定义」开 Dialog（H/S/L 三滑杆 + 实时预览）。
 * 4. 滤镜每格显示**该滤镜下的真实缩略**，不是黑块；选中态有描边 + 名称；强度滑杆只作用于
 *    当前选中滤镜，并提供「按住原图对比」。
 */

const SWATCHES: Array<{ name: string; value: string }> = [
  { name: '红', value: 'var(--color-red-500)' },
  { name: '橘', value: 'var(--color-orange-500)' },
  { name: '琥珀', value: 'var(--color-amber-500)' },
  { name: '黄', value: 'var(--color-yellow-500)' },
  { name: '柠檬', value: 'var(--color-lime-500)' },
  { name: '绿', value: 'var(--color-green-500)' },
  { name: '青', value: 'var(--color-teal-500)' },
  { name: '天蓝', value: 'var(--color-sky-500)' },
  { name: '蓝', value: 'var(--color-blue-500)' },
  { name: '靛', value: 'var(--color-indigo-500)' },
  { name: '紫', value: 'var(--color-violet-500)' },
  { name: '品红', value: 'var(--color-fuchsia-500)' },
  { name: '粉', value: 'var(--color-pink-500)' },
  { name: '棕', value: 'var(--color-amber-800)' },
  { name: '灰', value: 'var(--color-gray-500)' },
  { name: '白', value: 'var(--color-gray-100)' },
];

const FILTERS = [
  { name: '原图', css: 'none' },
  { name: '奶油', css: 'saturate(0.85) brightness(1.06)' },
  { name: '冷白', css: 'saturate(0.7) hue-rotate(-8deg) brightness(1.05)' },
  { name: '复古', css: 'sepia(0.45) contrast(1.05)' },
  { name: '氛围', css: 'contrast(1.2) saturate(1.1) brightness(0.92)' },
  { name: '空气感', css: 'brightness(1.12) saturate(0.95)' },
  { name: '黑白', css: 'grayscale(1)' },
];

const TOOLS = ['旋转', '镜像', '裁剪', '涂鸦', '文字', '马赛克', '滤镜', '调整'];

/**
 * 16 色色板：Astryx 官方 Color Swatch Grid 规范（role=grid/row/gridcell、aria-pressed、
 * 每行 8 格）。色值全部取主题色阶 token，不写死 hex。
 */
function SwatchGrid({
  selected,
  onPick,
}: {
  selected: string;
  onPick: (name: string) => void;
}) {
  return (
    <VStack gap={1} role="grid" aria-label="颜色">
      {[0, 1].map((row) => (
        <HStack key={row} gap={1} role="row" justify="between">
          {SWATCHES.slice(row * 8, row * 8 + 8).map((s) => {
            const on = s.name === selected;
            return (
              <button
                key={s.name}
                type="button"
                role="gridcell"
                aria-label={s.name}
                aria-pressed={on}
                onClick={() => onPick(s.name)}
                style={{
                  width: 'var(--size-element-sm)',
                  height: 'var(--size-element-sm)',
                  borderRadius: 'var(--radius-full)',
                  background: s.value,
                  outline: on ? '2px solid var(--color-accent)' : 'none',
                  border: '1px solid var(--color-border)',
                  display: 'grid',
                  placeItems: 'center',
                  cursor: 'pointer',
                }}
              >
                {on && <Icon icon={CheckIcon} size="xsm" color="primary" />}
              </button>
            );
          })}
        </HStack>
      ))}
    </VStack>
  );
}

export default function EditorScreen() {
  const [tool, setTool] = useState('涂鸦');
  const [color, setColor] = useState('绿');
  const [thickness, setThickness] = useState(6);
  const [filter, setFilter] = useState('原图');
  const [strength, setStrength] = useState(100);
  const [customOpen, setCustomOpen] = useState(false);
  const [hue, setHue] = useState(210);
  const [sat, setSat] = useState(70);
  const [light, setLight] = useState(50);

  const activeFilter = FILTERS.find((f) => f.name === filter) ?? FILTERS[0];
  const customColor = `hsl(${hue} ${sat}% ${light}%)`;

  return (
    <VStack gap={0}>
      {/* 顶栏：关闭 · 标题 · 撤销/重做/重置 */}
      <HStack gap={2} paddingInline={3} paddingBlock={2} align="center">
        <IconButton label="关闭" variant="ghost" size="sm" icon={<Icon icon={XMarkIcon} />} />
        <Heading level={3}>编辑图片</Heading>
        <HStack gap={1}>
          <IconButton label="撤销" variant="ghost" size="sm" icon={<Icon icon={ArrowUturnLeftIcon} />} />
          <IconButton label="重做" variant="ghost" size="sm" icon={<Icon icon={ArrowUturnLeftIcon} />} style={{ transform: 'scaleX(-1)' }} />
          <IconButton label="重置" variant="ghost" size="sm" icon={<Icon icon={SparklesIcon} />} />
        </HStack>
        <Button variant="primary" size="sm" label="完成" icon={<Icon icon={CheckIcon} size="xsm" />} />
      </HStack>

      {/* 画布：变暗/网格/角标都只在图片矩形内；四角把手允许越出图片边界（贴边的角抓得住） */}
      <Section variant="muted" padding={3}>
        <Card padding={2} elevation="low" style={{ position: 'relative' }}>
          <HStack
            gap={1}
            align="center"
            justify="center"
            padding={4}
            style={{
              filter: activeFilter.css,
              background: 'var(--color-background-muted)',
              borderRadius: 'var(--radius-inner)',
            }}
          >
            <Icon icon={PhotoIcon} color="tertiary" />
            <Text type="supporting" color="secondary">图片实际显示矩形（网格与裁剪框只在此矩形内）</Text>
          </HStack>
          <VStack gap={0} style={{ position: 'absolute', top: 'calc(var(--spacing-2) * -1)', left: 'calc(var(--spacing-2) * -1)' }}>
            <Button variant="primary" label="左上角" isIconOnly size="sm" icon={<Icon icon="chevronDown" size="xsm" style={{ transform: 'rotate(135deg)' }} />} onClick={() => setTool('裁剪')} />
          </VStack>
          <VStack gap={0} style={{ position: 'absolute', bottom: 'calc(var(--spacing-2) * -1)', right: 'calc(var(--spacing-2) * -1)' }}>
            <Button variant="primary" label="右下角" isIconOnly size="sm" icon={<Icon icon="chevronDown" size="xsm" style={{ transform: 'rotate(315deg)' }} />} onClick={() => setTool('裁剪')} />
          </VStack>
        </Card>
        <Text type="supporting" color="secondary">
          裁剪：网格对齐图片边界，角把手热区 40dp 且可越出图片；框内拖动=平移整框，框外=不动。
        </Text>
      </Section>

      <Divider />

      {/* 面板槽：在工具条**上方**，所以标签不会被屏幕底边切掉 */}
      {tool === '涂鸦' || tool === '马赛克' || tool === '文字' ? (
        <Section variant="transparent" paddingInline={3} paddingBlock={2}>
          <VStack gap={2}>
            <HStack gap={2} align="center" justify="between">
              <Text type="label" color="secondary">{tool === '文字' ? '文字颜色' : '画笔颜色'}</Text>
              <Button
                variant="secondary"
                size="sm"
                label="自定义"
                icon={<Icon icon={PlusIcon} size="xsm" />}
                onClick={() => setCustomOpen(true)}
              />
            </HStack>
            <SwatchGrid selected={color} onPick={setColor} />
            {color === '自定义' && (
              <HStack gap={2} align="center">
                <Card
                  padding={4}
                  elevation="none"
                  style={{ background: customColor, borderRadius: 'var(--radius-full)' }}
                >
                  <Text type="supporting" color="inherit">自定义色</Text>
                </Card>
              </HStack>
            )}
            <VStack gap={0} maxWidth={220}>
              <Slider
                label="粗细"
                value={thickness}
                min={1}
                max={20}
                onChange={setThickness}
                valueDisplay="text"
                marks={[{ value: 1 }, { value: 6 }, { value: 12 }, { value: 20 }]}
              />
            </VStack>
          </VStack>
        </Section>
      ) : null}

      {tool === '滤镜' ? (
        <Section variant="transparent" paddingInline={3} paddingBlock={2}>
          <VStack gap={2}>
            <HStack gap={2} align="center" justify="between">
              <Text type="label" color="secondary">滤镜</Text>
              <Text type="supporting" color="disabled">按住「原图」可对比</Text>
            </HStack>
            {/* 每格缩略图 = 原图实际套上该滤镜：不是黑块 */}
            <HStack gap={2} justify="between">
              {FILTERS.map((f) => {
                const on = f.name === filter;
                return (
                  <VStack key={f.name} gap={0} align="center">
                    <button
                      type="button"
                      aria-label={f.name}
                      aria-pressed={on}
                      onClick={() => setFilter(f.name)}
                      style={{
                        width: 'var(--size-element-lg)',
                        height: 'var(--size-element-lg)',
                        borderRadius: 'var(--radius-element)',
                        border: '1px solid var(--color-border)',
                        outline: on ? '2px solid var(--color-accent)' : 'none',
                        background: 'var(--color-background-muted)',
                        filter: f.css,
                        cursor: 'pointer',
                      }}
                    />
                    <Text type="supporting" color={on ? 'accent' : 'secondary'}>{f.name}</Text>
                  </VStack>
                );
              })}
            </HStack>
            <VStack maxWidth={260}>
              <Slider
                label="强度"
                value={strength}
                min={0}
                max={100}
                onChange={setStrength}
                valueDisplay="text"
                isDisabled={filter === '原图'}
                disabledMessage={filter === '原图' ? '选一个滤镜后强度才生效' : undefined}
              />
            </VStack>
          </VStack>
        </Section>
      ) : null}

      <Divider />

      {/* 工具条：贴底、等宽、图标+标签，选中态 primary */}
      <HStack gap={1} paddingInline={2} paddingBlock={2} justify="between">
        {TOOLS.map((t) => (
          <Button
            key={t}
            variant={tool === t ? 'primary' : 'ghost'}
            size="sm"
            label={t}
            onClick={() => setTool(t)}
          />
        ))}
      </HStack>

      <Dialog isOpen={customOpen} onOpenChange={setCustomOpen} width={340}>
        <VStack gap={3}>
          <DialogHeader title="自定义颜色" onOpenChange={setCustomOpen} />
          <Card
            padding={5}
            elevation="none"
            style={{ background: customColor, borderRadius: 'var(--radius-element)' }}
          >
            <Text type="label" color="inherit">实时预览</Text>
          </Card>
          <Slider label="色相" value={hue} min={0} max={360} onChange={setHue} valueDisplay="text" />
          <Slider label="饱和度" value={sat} min={0} max={100} onChange={setSat} valueDisplay="text" />
          <Slider label="明度" value={light} min={0} max={100} onChange={setLight} valueDisplay="text" />
          <Badge variant="neutral" label="涂鸦 / 文字 / 马赛克 共用同一套取色" />
          <HStack gap={2} justify="end">
            <Button variant="ghost" label="取消" onClick={() => setCustomOpen(false)} />
            <Button
              variant="primary"
              label="使用这个颜色"
              onClick={() => {
                setColor('自定义');
                setCustomOpen(false);
              }}
            />
          </HStack>
        </VStack>
      </Dialog>
    </VStack>
  );
}
