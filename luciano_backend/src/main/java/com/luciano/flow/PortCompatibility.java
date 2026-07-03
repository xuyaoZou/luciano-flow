package com.luciano.flow;

/**
 * 端口类型兼容规则
 * <p>
 * 定义工作流连线时，输出端口能否连接到输入端口。
 * 参考设计文档 §3.3 类型兼容规则。
 * <p>
 * 规则：
 * - 同类型直接兼容
 * - IMAGE 可连 REFERENCE 和 MASK
 * - VIDEO 可连 REFERENCE
 * - REFERENCE 可连 IMAGE
 * - 其他跨类型不兼容
 */
public final class PortCompatibility {

    private PortCompatibility() {
        // 工具类不允许实例化
    }

    /**
     * 输出端口能否连接到输入端口
     *
     * @param output 输出端口的类型
     * @param input  输入端口的类型
     * @return true 表示可以连线
     */
    public static boolean canConnect(PortType output, PortType input) {
        if (output == input) {
            return true;  // 同类型直接兼容
        }

        return switch (output) {
            case IMAGE -> input == PortType.REFERENCE || input == PortType.MASK;
            case VIDEO -> input == PortType.REFERENCE;
            case REFERENCE -> input == PortType.IMAGE;
            default -> false;
        };
    }
}