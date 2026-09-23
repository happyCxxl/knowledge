package com.knowledge.biz.service.db.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.biz.mapper.KbIndexVersionMapper;
import com.knowledge.biz.service.db.KbIndexVersionDbService;
import com.knowledge.common.domain.entity.KbIndexVersion;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 索引版本数据访问服务实现（step-13 B08）。
 *
 * @author cxxl
 */
@Service
public class KbIndexVersionDbServiceImpl extends ServiceImpl<KbIndexVersionMapper, KbIndexVersion>
        implements KbIndexVersionDbService {

    @Override
    public List<KbIndexVersion> listByIndexSetId(Long indexSetId) {
        LambdaQueryWrapper<KbIndexVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbIndexVersion::getIndexSetId, indexSetId)
                .orderByDesc(KbIndexVersion::getId);
        return list(queryWrapper);
    }

    @Override
    public String nextVersionNo(Long indexSetId) {
        // 取现有最大数字后缀 + 1：回收会物理删行，行数+1 在删行后会重号；
        // 并发冲突仍由 uk_set_version 兜底（调用方捕获后重取，此时 MAX 已读到赢家新值）
        QueryWrapper<KbIndexVersion> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(MAX(CAST(SUBSTRING(version_no, 2) AS UNSIGNED)), 0)")
                .eq("index_set_id", indexSetId);
        List<Object> result = baseMapper.selectObjs(queryWrapper);
        long max = ObjectUtil.isNull(result) || result.isEmpty() || ObjectUtil.isNull(result.getFirst())
                ? 0L : ((Number) result.getFirst()).longValue();
        return "v" + (max + 1);
    }

    @Override
    public KbIndexVersion getByTaskId(Long taskId) {
        LambdaQueryWrapper<KbIndexVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbIndexVersion::getTaskId, taskId)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }
}
