package com.moli.agent.scope.harness.artifact;

import com.moli.agent.scope.common.dao.entity.MChatArtifact;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地产物存储：把对象写到配置好的根目录下，仅用于开发环境兜底。
 *
 * <p>两道防线：
 * 一、逻辑 key 先做「必须留在根目录内」的归一化校验，防止 key 里塞 {@code ..} 越出去读写系统文件
 * （表里的 object_key 是字符串，一旦被人改成 {@code ../../application-local.yml}，没有这道校验就等于任意文件读取）；
 * 二、写入走「临时文件 + 原子移动」，避免下游在读的瞬间拿到半截文件。
 *
 * <p>生产环境请用 {@link OssArtifactStorage}：本机盘上的产物在多副本部署下只有处理这次请求的那台机器能取到，
 * 且实例销毁即丢失。
 *
 * @author moli
 * @since 2026-11-13
 */
@Slf4j
public class LocalArtifactStorage implements ArtifactStorage {

    /** 本地根目录的绝对化形式，用于越界校验 */
    private final Path root;

    public LocalArtifactStorage(ArtifactProperties properties) {
        this.root = Paths.get(properties.getLocal().getRoot()).toAbsolutePath().normalize();
        log.info("产物存储使用本地目录: root={}（多副本部署请切 agent.artifact.storage=oss）", root);
    }

    @Override
    public String type() {
        return MChatArtifact.STORAGE_LOCAL;
    }

    @Override
    public long put(String objectKey, byte[] content, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            // 先写同目录临时文件再替换：读侧不会拿到写了一半的内容。
            // 这里不用 ATOMIC_MOVE：Windows 上目标已存在时它会直接报错，而同名 key 覆盖是正常语义
            Path temp = Files.createTempFile(target.getParent(), ".artifact-", ".tmp");
            try {
                Files.write(temp, content);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                // 移动成功时此处是空操作，只有中途抛异常才需要清掉残留的临时文件
                Files.deleteIfExists(temp);
            }
            return content.length;
        } catch (IOException e) {
            throw new UncheckedIOException("本地产物写入失败: " + target, e);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            return null;
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new UncheckedIOException("本地产物读取失败: " + target, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolve(objectKey);
        try {
            // deleteIfExists 天然幂等：对象不在就等于删好了
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("本地产物删除失败（不阻断主流程）: path={}, error={}", target, e.getMessage());
        }
    }

    /**
     * 把逻辑 key 解析成根目录下的绝对路径，并挡住越出根目录的 key。
     * <p>key 用 {@code /} 分隔，Windows 下需要换成本地分隔符再解析。
     */
    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        Path relative = Paths.get(objectKey.replace('/', File.separatorChar));
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("产物 key 越出存储根目录: objectKey=" + objectKey + ", root=" + root);
        }
        return target;
    }
}
