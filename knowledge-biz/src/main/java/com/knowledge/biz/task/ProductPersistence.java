package com.knowledge.biz.task;

import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 阶段产物落库共用助手（各环节任务执行器共用）：
 * JSON 序列化 → 内容寻址写产物存储 → 产物行（stage/upstream/能力快照）→ 回写任务产物引用。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class ProductPersistence {

    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final FileStorage fileStorage;

    /**
     * 落库阶段产物并回写任务产物引用；返回产物行（artifactId = sha256 = contentHash）。
     *
     * @param task              所属任务（回写 productId）
     * @param stage             产出环节
     * @param upstreamProductId 上游产物 ID（可空）
     * @param capabilitySnapshot 能力/策略版本快照（JSON 文本）
     * @param payload           产物本体（JSON 序列化后写产物存储）
     */
    public KbPipelineProduct persist(KbPipelineTask task, PipelineStage stage, Long upstreamProductId,
                                     String capabilitySnapshot, Object payload) {
        String json = Objects.requireNonNull(JsonUtil.toJsonStr(Objects.requireNonNull(payload, "产物内容为空")),
                "产物序列化失败");
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        String artifactId = fileStorage.putObject(content);

        KbPipelineProduct product = new KbPipelineProduct();
        product.setFileResultId(task.getFileResultId());
        product.setStage(stage.name());
        product.setUpstreamProductId(upstreamProductId);
        product.setCapabilitySnapshot(capabilitySnapshot);
        product.setArtifactId(artifactId);
        product.setContentHash(artifactId);
        product.setStatus(RowStatus.ACTIVE.name());
        pipelineProductDbService.save(product);
        pipelineTaskDbService.updateProductId(task.getId(), product.getId());
        return product;
    }
}
