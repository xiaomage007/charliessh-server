package com.charlie.ai.domain.agent.service;

import com.charlie.ai.domain.agent.model.valobj.AiAgentConfigTableVO;

import java.util.List;

/**
 * @description: 装配接口
 * @author: Charlie
 * @date: 2026/10/14 9:24
 */
public interface IArmoryService {

    void acceptArmoryAgents(List<AiAgentConfigTableVO> tables) throws Exception;

}
