import { useEffect, useState } from 'react';
import { Theme } from '@astryxdesign/core/theme';
import { themes, defaultThemeSlug } from './astryx-themes';
import '@astryxdesign/core/reset.css';
import '@astryxdesign/core/astryx.css';
import { Layout } from '@astryxdesign/core/Layout';
import { VStack } from '@astryxdesign/core/VStack';
import { HStack } from '@astryxdesign/core/HStack';
import { Card } from '@astryxdesign/core/Card';
import { Section } from '@astryxdesign/core/Section';
import { Text } from '@astryxdesign/core/Text';
import { Heading } from '@astryxdesign/core/Heading';
import { Divider } from '@astryxdesign/core/Divider';
import { Icon } from '@astryxdesign/core/Icon';
import { Button } from '@astryxdesign/core/Button';
import { Badge } from '@astryxdesign/core/Badge';
import { Collapsible } from '@astryxdesign/core/Collapsible';
import EditorScreen from './EditorScreen';
import {
  LightBulbIcon,
  PhotoIcon,
  ArrowUturnLeftIcon,
  EllipsisHorizontalIcon,
} from '@heroicons/react/24/outline';

/* ================= 解题结果页 ================= */

const THINKING =
  '审题：已知 x² − 5x + 6 = 0，求根。这是一元二次方程，先试因式分解；' +
  '若判别式不是完全平方再退到求根公式。容易错的地方：把两根之和的符号写反。';

const STEPS = [
  '写成标准形式 x² − 5x + 6 = 0，其中 a = 1、b = −5、c = 6。',
  '因式分解：找两数之积为 6、之和为 5，即 2 和 3 → (x − 2)(x − 3) = 0。',
  '令每个因式为零：x − 2 = 0 或 x − 3 = 0。',
  '检验：2 + 3 = 5、2 × 3 = 6，与系数一致。',
];

function SolveScreen() {
  const [revealed, setRevealed] = useState(STEPS.length);
  const [streaming, setStreaming] = useState(false);

  useEffect(() => {
    if (!streaming) return;
    if (revealed >= STEPS.length) {
      setStreaming(false);
      return;
    }
    const t = setTimeout(() => setRevealed((n) => n + 1), 700);
    return () => clearTimeout(t);
  }, [streaming, revealed]);

  const replay = () => {
    setRevealed(0);
    setStreaming(true);
  };

  return (
    <VStack gap={4}>
      <HStack gap={2}>
        <Heading level={2}>拍照解题</Heading>
        <Badge variant="info" label="数学" />
        {streaming && <Badge variant="warning" label="正在生成" />}
      </HStack>

      <Card padding={3} elevation="low">
        <VStack gap={1}>
          <Text type="label" color="secondary">题目</Text>
          <Text type="large" weight="medium">解方程 x² − 5x + 6 = 0</Text>
        </VStack>
      </Card>

      {/* 思考过程：默认折叠，摘要一行，字体比正式步骤小一号、颜色退到 secondary */}
      <Card variant="muted" padding={0}>
        <Collapsible
          defaultIsOpen={false}
          trigger={
            <HStack gap={2}>
              <Icon icon={LightBulbIcon} color="secondary" size="sm" />
              <VStack gap={0}>
                <Text type="label" color="secondary">思考过程</Text>
                <Text type="supporting" color="secondary" maxLines={1}>
                  先因式分解，判别式兜底；注意根的和符号
                </Text>
              </VStack>
            </HStack>
          }
        >
          <VStack gap={1} paddingInline={4} paddingBlockEnd={3}>
            <Divider />
            <Text type="supporting" color="secondary" style={{ lineHeight: 1.8 }}>
              {THINKING}
            </Text>
          </VStack>
        </Collapsible>
      </Card>

      {/* 正式解题步骤：编号 + 正文体，行距放宽，逐条流式出现 */}
      <Section variant="transparent" padding={0}>
        <VStack gap={3}>
          <Heading level={3}>解题步骤</Heading>
          {STEPS.slice(0, revealed).map((step, i) => (
            <HStack key={i} gap={3} align="start">
              <Text type="label" color="accent" weight="semibold" hasTabularNumbers>
                {String(i + 1).padStart(2, '0')}
              </Text>
              <Text type="body" style={{ lineHeight: 1.8 }}>{step}</Text>
            </HStack>
          ))}
          {streaming && (
            <HStack gap={2}>
              <Icon icon="clock" color="tertiary" size="sm" />
              <Text type="supporting" color="disabled">下一步生成中…</Text>
            </HStack>
          )}
        </VStack>
      </Section>

      <Card variant="green" padding={3}>
        <VStack gap={1}>
          <Text type="label" color="secondary">最终答案</Text>
          <Heading level={3}>x = 2 或 x = 3</Heading>
        </VStack>
      </Card>

      <HStack gap={2}>
        <Button variant="ghost" label="重放流式效果" icon={<Icon icon={ArrowUturnLeftIcon} size="sm" />} onClick={replay} />
        <Button variant="primary" label="再拍一题" icon={<Icon icon={PhotoIcon} size="sm" />} onClick={replay} />
      </HStack>
    </VStack>
  );
}

/* ================= 页面骨架 ================= */

export default function App() {
  return (
    <Theme theme={themes[defaultThemeSlug]}>
      <Layout
        header={
          <HStack gap={2} paddingInline={4} paddingBlock={3}>
            <Icon icon="chevronLeft" />
            <Heading level={1}>拍照解题 + 图片编辑 · UI 样稿</Heading>
            <Icon icon={EllipsisHorizontalIcon} />
          </HStack>
        }
      >
        <VStack gap={8} maxWidth={460}>
          <SolveScreen />
          <Divider variant="strong" label="图片编辑页" />
          <EditorScreen />
        </VStack>
      </Layout>
    </Theme>
  );
}
