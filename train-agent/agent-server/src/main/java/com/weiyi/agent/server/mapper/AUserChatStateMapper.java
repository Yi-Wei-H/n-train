package com.weiyi.agent.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiyi.agent.server.entity.AUserChatState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AUserChatStateMapper extends BaseMapper<AUserChatState> {

    /** 并发首聊安全建行：冲突忽略（不中断事务），随后 selectForUpdate 等锁重读 */
    @Insert("INSERT INTO a_user_chat_state (user_id) VALUES (#{userId}) ON CONFLICT (user_id) DO NOTHING")
    int insertIgnore(@Param("userId") Long userId);

    /** 指针行锁：发送链路在同一事务内串行化首建/翻篇/占用判定 */
    @Select("SELECT * FROM a_user_chat_state WHERE user_id = #{userId} FOR UPDATE")
    AUserChatState selectForUpdate(@Param("userId") Long userId);
}
