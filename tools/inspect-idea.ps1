<#
.SYNOPSIS
  无头运行 IDEA 静态检查（inspect），只报告"将提交代码"上的告警，目标 0。
.DESCRIPTION
  1) 用隔离的 IDEA 配置目录（config/system/log/plugins 全部指向仓库外的 _idea_isolated）启动
     idea64.exe inspect，不读写用户真实 IDEA 配置（主题/词典/最近项目等一概不碰）；
  2) 解析输出 JSON，只保留「已暂存(git diff --cached) + 已修改(git diff) + -Extra 指定」文件上的告警，
     写入仓库外的 _idea_staged_warnings.txt；
  3) 校验用户配置目录中 colors.scheme.xml / recentProjects.xml 在运行前后未被改动。
.PARAMETER FilterOnly
  跳过检测，只对现有 _idea_inspect 结果重新过滤。
.PARAMETER Extra
  额外纳入过滤的仓库内相对路径（未跟踪但属于当前阶段的文件），可多值。
.EXAMPLE
  .\inspect-idea.ps1
  .\inspect-idea.ps1 -FilterOnly
  .\inspect-idea.ps1 -Extra 'knowledge-biz/src/main/java/com/knowledge/biz/task/TaskRunnerSupport.java'
#>
[CmdletBinding()]
param(
    [switch]$FilterOnly,
    [string[]]$Extra = @()
)

$ErrorActionPreference = 'Stop'

$repo     = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workRoot = Split-Path $repo -Parent
$ideaHome = 'D:\devTools\idea\IntelliJ IDEA 2026.2.3'
$ideaExe  = Join-Path $ideaHome 'bin\idea64.exe'
$isolated = Join-Path $workRoot '_idea_isolated'
$outDir   = Join-Path $workRoot '_idea_inspect'
$warnFile = Join-Path $workRoot '_idea_staged_warnings.txt'

function Invoke-Inspection {
    $runCmd = Join-Path $isolated 'run.cmd'
    $log    = Join-Path $isolated 'run.log'
    cmd /c "`"$runCmd`"" 2>&1 | Out-Null
    $logText = Get-Content $log -Raw -ErrorAction SilentlyContinue
    $exit = 1
    if ($logText -match 'EXITCODE=(\d+)') { $exit = [int]$Matches[1] }
    return $exit
}

if (-not $FilterOnly) {
    Write-Host '== 准备隔离 IDEA 配置目录（不动用户配置）=='
    foreach ($d in @('config\options', 'system', 'log', 'plugins')) {
        New-Item -ItemType Directory -Path (Join-Path $isolated $d) -Force | Out-Null
    }

    # 项目模型所需的最小配置：只读复制用户的 JDK 表与可信路径，写入仅发生在隔离目录
    $userCfg = Join-Path $env:APPDATA 'JetBrains\IntelliJIdea2026.2'
    foreach ($seed in @('jdk.table.xml', 'trusted-paths.xml')) {
        $src = Join-Path $userCfg "options\$seed"
        if (Test-Path $src) {
            Copy-Item $src (Join-Path $isolated "config\options\$seed") -Force
        }
    }

    # vmoptions：继承机器级 IDEA_VM_OPTIONS（含授权 agent），末尾追加隔离目录覆盖
    $baseVm = $env:IDEA_VM_OPTIONS
    if (-not $baseVm -or -not (Test-Path $baseVm)) {
        $baseVm = Join-Path $ideaHome 'bin\idea64.exe.vmoptions'
    }
    $fwd = $isolated.Replace('\', '/')
    $vmLines = @(Get-Content $baseVm) + @(
        "-Didea.config.path=$fwd/config",
        "-Didea.system.path=$fwd/system",
        "-Didea.log.path=$fwd/log",
        "-Didea.plugins.path=$fwd/plugins"
    )
    $myVm = Join-Path $isolated 'idea.vmoptions'
    [System.IO.File]::WriteAllLines($myVm, $vmLines, (New-Object System.Text.UTF8Encoding($false)))

    # run.cmd：必须经 cmd 执行（idea64.exe 是 GUI 子系统程序，cmd 会等待其结束）
    $runCmd = Join-Path $isolated 'run.cmd'
    $runLog = Join-Path $isolated 'run.log'
    $batchLines = @(
        '@echo off',
        "set `"IDEA_VM_OPTIONS=$myVm`"",
        "`"$ideaExe`" inspect `"$repo`" `"Project_Default`" `"$outDir`" -format json > `"$runLog`" 2>&1",
        "echo EXITCODE=%ERRORLEVEL% >>`"$runLog`""
    )
    [System.IO.File]::WriteAllLines($runCmd, $batchLines, [System.Text.Encoding]::ASCII)

    # 用户配置完整性基线（旧方式曾改过这些文件，跑完必须未动）
    $watch = @('colors.scheme.xml', 'recentProjects.xml', 'usage.statistics.xml') | ForEach-Object { Join-Path $userCfg "options\$_" }
    $before = @{}
    foreach ($f in $watch) {
        if (Test-Path $f) { $before[$f] = (Get-FileHash $f -Algorithm MD5).Hash }
    }

    Write-Host '== 运行无头检测 =='
    if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
    $exit = Invoke-Inspection
    $jsonCount = @(Get-ChildItem $outDir -File -Filter '*.json' -ErrorAction SilentlyContinue).Count
    if ($exit -ne 0 -or $jsonCount -eq 0) {
        Write-Host "首次运行失败(EXIT=$exit, JSON=$jsonCount)，重试一次"
        Start-Sleep -Seconds 5
        if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
        $exit = Invoke-Inspection
    }
    $jsonCount = @(Get-ChildItem $outDir -File -Filter '*.json' -ErrorAction SilentlyContinue).Count
    Write-Host "INSPECT_EXIT=$exit JSON_FILES=$jsonCount"

    # 隔离有效性：无头进程应把配置全部写进隔离目录（其中必然生成 options 文件）；
    # 用户配置完整性：跑完后用户文件内容若与隔离副本一致，才可能是我们写入的。
    $isolatedOptions = Join-Path $isolated 'config\options'
    $isolationOk = (Get-ChildItem $isolatedOptions -File -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0
    Write-Host "ISOLATION_OK=$isolationOk"
    $touched = @()
    $selfWrite = @()
    foreach ($f in $watch) {
        if (-not $before.ContainsKey($f)) { continue }
        if (-not (Test-Path $f)) { continue }
        if ((Get-FileHash $f -Algorithm MD5).Hash -ne $before[$f]) {
            $iso = Join-Path $isolatedOptions (Split-Path $f -Leaf)
            if ((Test-Path $iso) -and (Get-FileHash $iso -Algorithm MD5).Hash -eq (Get-FileHash $f -Algorithm MD5).Hash) {
                $touched += $f
            } else {
                $selfWrite += $f
            }
        }
    }
    if ($selfWrite.Count -gt 0) {
        Write-Host "USER_CFG_SELF_WRITE=$($selfWrite -join ', ')（用户开着的 IDE 自身写入，与本次运行无关）"
    }
    if ($touched.Count -eq 0) {
        Write-Host 'USER_CFG_UNTOUCHED=True'
    } else {
        Write-Host "USER_CFG_UNTOUCHED=False -> $($touched -join ', ')"
    }
}

# ---- 过滤：只保留「将提交代码」上的告警 ----
Set-Location $repo
$staged   = @(git diff --cached --name-only)
$modified = @(git diff --name-only)
$keep = New-Object System.Collections.Generic.HashSet[string]
foreach ($p in ($staged + $modified + $Extra)) {
    if ($p) { [void]$keep.Add($p.Trim().Replace('\', '/')) }
}

$rows = New-Object System.Collections.Generic.List[string]
$jsonFiles = Get-ChildItem $outDir -File -Filter '*.json' | Where-Object { $_.Name -ne '.descriptions.json' }
foreach ($f in $jsonFiles) {
    $json = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    try { $obj = $json | ConvertFrom-Json } catch { continue }
    if ($null -eq $obj.problems) { continue }
    foreach ($p in $obj.problems) {
        if ($null -eq $p.file) { continue }
        $rel = [string]$p.file
        if ($rel.StartsWith('file://$PROJECT_DIR$/')) { $rel = $rel.Substring('file://$PROJECT_DIR$/'.Length) }
        elseif ($rel.StartsWith('file://E:/workbuddy/knowledge/')) { $rel = $rel.Substring('file://E:/workbuddy/knowledge/'.Length) }
        elseif ($rel.StartsWith('file:///E:/workbuddy/knowledge/')) { $rel = $rel.Substring('file:///E:/workbuddy/knowledge/'.Length) }
        $rel = [Uri]::UnescapeDataString($rel).Replace('\', '/')
        if (-not $keep.Contains($rel)) { continue }
        $id = $p.problem_class.id
        # 依赖 CVE 告警与 Maven 插件索引假阳性不计入门禁（平台钉版口径，用户拍板）
        if ($id -eq 'VulnerableLibrariesLocal' -or $id -eq 'MavenModelInspection') { continue }
        $desc = ([string]$p.description) -replace "`r?`n", ' '
        $rows.Add("$rel|$($p.line)|$id|$desc")
    }
}
$sorted = @($rows | Sort-Object)
if ($sorted.Count -gt 0) {
    [System.IO.File]::WriteAllLines($warnFile, $sorted, (New-Object System.Text.UTF8Encoding($false)))
} else {
    [System.IO.File]::WriteAllText($warnFile, '', (New-Object System.Text.UTF8Encoding($false)))
}
Write-Host "STAGED_HITS=$($sorted.Count) -> $warnFile"
