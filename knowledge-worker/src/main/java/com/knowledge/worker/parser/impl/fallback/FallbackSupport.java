package com.knowledge.worker.parser.impl.fallback;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.parse.QualityInfo;

/**
 * 信号降级公共口径（package-private，各 handler 共用）：
 * region 页码解析与失败/扫描页登记。
 *
 * @author cxxl
 */
final class FallbackSupport {

    private FallbackSupport() {
    }

    /** 信号 region 解析页码（"page 3"→3）；解析不到返回 null（Word/Excel 无页概念）。 */
    static Integer parsePage(String region) {
        try {
            String num = region.replace("page", "").trim();
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 失败页登记（参与 90% 门槛）。 */
    static void addFailedPage(QualityInfo quality, Integer page) {
        if (ObjectUtil.isNotNull(page)) {
            quality.getFailedPages().add(page);
        }
    }

    /** 扫描页登记（失败单元与整任务判定共用）。 */
    static void addScannedPage(QualityInfo quality, Integer page) {
        if (ObjectUtil.isNotNull(page)) {
            quality.getScannedPages().add(page);
        }
    }
}
