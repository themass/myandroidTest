package com.livekit.voiceassistant.architecture

/**
 * 组件层次结构定义
 * 明确各层职责边界，避免职责重叠
 */

/**
 * Screen层职责：
 * - 页面级别的UI组合
 * - 页面状态管理
 * - 页面导航逻辑
 * - 业务逻辑协调
 */
interface ScreenLayer {
    // Screen层应该只包含页面级别的逻辑
    // 不包含具体的UI组件实现
}

/**
 * Component层职责：
 * - 可复用的UI组件
 * - 组件内部状态管理
 * - 组件特定的业务逻辑
 * - 组件与外部交互的接口
 */
interface ComponentLayer {
    // Component层应该包含可复用的UI组件
    // 每个组件应该有明确的职责和接口
}

/**
 * UI层职责：
 * - 基础UI组件
 * - 主题和样式
 * - 动画效果
 * - 布局组件
 */
interface UILayer {
    // UI层应该包含最基础的UI元素
    // 不包含业务逻辑，只负责展示
}

/**
 * 职责分离原则：
 * 
 * 1. Screen层：
 *    - 组合多个Component
 *    - 管理页面级状态
 *    - 处理页面导航
 *    - 协调业务逻辑
 * 
 * 2. Component层：
 *    - 实现特定功能
 *    - 管理组件状态
 *    - 提供组件接口
 *    - 可跨页面复用
 * 
 * 3. UI层：
 *    - 基础UI元素
 *    - 主题样式
 *    - 动画效果
 *    - 布局组件
 * 
 * 4. 禁止跨层调用：
 *    - Screen不能直接调用UI层
 *    - Component不能直接调用其他Component
 *    - UI层不能包含业务逻辑
 */
