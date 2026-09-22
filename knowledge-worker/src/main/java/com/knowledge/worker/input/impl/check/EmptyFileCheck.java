package com.knowledge.worker.input.impl.check;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.enums.input.FileCheckStepName;
import com.knowledge.common.enums.input.FileValidationFailReason;
import com.knowledge.worker.input.check.FileCheck;
import com.knowledge.worker.input.FileCheckContext;
import com.knowledge.common.domain.input.FileCheckResult;
import org.springframework.stereotype.Component;

/**
 * 空文件判定步骤：流式统计字节数为 0 视为损坏（空文件无可解析内容）。
 *
 * @author cxxl
 */
@Component
public class EmptyFileCheck implements FileCheck {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public FileCheckStepName name() {
        return FileCheckStepName.EMPTY_FILE;
    }

    @Override
    public FileCheckResult check(FileCheckContext context) {
        if (ObjectUtil.isNotNull(context.getSize()) && context.getSize() == 0) {
            return FileCheckResult.fail(FileValidationFailReason.FILE_CORRUPTED);
        }
        return FileCheckResult.pass();
    }
}
