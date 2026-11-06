package com.weiyi.agent.server.ai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 思考块剥离测试：覆盖标签整段/跨段切分、开头空白、未闭合、无思考直通、
 * 判定期跨段（&lt;th + ink&gt;）、中途标签不剥离、非流式整段剥离。
 */
class ThinkTagStripperTest {

    private static String stream(String... deltas) {
        ThinkTagStripper stripper = new ThinkTagStripper();
        StringBuilder out = new StringBuilder();
        for (String d : deltas) {
            out.append(stripper.feed(d));
        }
        out.append(stripper.flush());
        return out.toString();
    }

    @Test
    @DisplayName("整段一次到达：剥思考块，正文首个换行一并剥掉")
    void testSingleDelta() {
        String out = stream("<think>推理过程</think>\n你好，需要查票吗？");
        assertEquals("你好，需要查票吗？", out);
    }

    @Test
    @DisplayName("标签与闭合跨 delta 切分：状态机正确拼接判定")
    void testSplitTags() {
        String out = stream("<th", "ink>思考", "中</thi", "nk>正文", "来了");
        assertEquals("正文来了", out);
    }

    @Test
    @DisplayName("思考内容跨多段且未闭合：流结束整体丢弃")
    void testUnclosedDropped() {
        String out = stream("<think>一直", "在思考", "没有结束");
        assertEquals("", out);
    }

    @Test
    @DisplayName("无思考块：全部直通（含开头普通文本）")
    void testNoThinkTag() {
        String out = stream("直接", "回答");
        assertEquals("直接回答", out);
    }

    @Test
    @DisplayName("判定期跨段且最终不是思考块：缓冲内容放行")
    void testProbeNotThink() {
        String out = stream("<t", "itle>不是思考</title>");
        assertEquals("<title>不是思考</title>", out);
    }

    @Test
    @DisplayName("开头空白 + 思考块：识别并剥离")
    void testLeadingWhitespace() {
        String out = stream("  <think>x</think>\n答案");
        assertEquals("  答案".substring(2), out);
    }

    @Test
    @DisplayName("中途出现的 <think> 标签不剥离（正文中的标签多为内容回显/代码）")
    void testMidStreamTagKept() {
        String out = stream("正文里出现 <think>demo</think> 代码示例");
        assertEquals("正文里出现 <think>demo</think> 代码示例", out);
    }

    @Test
    @DisplayName("非流式整段剥离：stripLeading")
    void testStripLeading() {
        assertEquals("标题", ThinkTagStripper.stripLeading("<think>想标题</think>\n标题"));
        assertEquals("", ThinkTagStripper.stripLeading("<think>未闭合"));
        assertEquals("普通文本", ThinkTagStripper.stripLeading("普通文本"));
    }
}
