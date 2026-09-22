<#
.SYNOPSIS
  规范 Markdown 表格样式：复刻 IntelliJ IDEA 的 Reformat Table 行为（纯脚本，无需 IDEA）。

.DESCRIPTION
  对齐规则与 IDEA 一致（已用 IDEA 实际输出做字节级校验）：
    - 列宽 = 该列最大显示宽度（中文/全角按 2 个宽度计，ASCII 按 1）；
    - 内容行 | 内容 |：单元格补空格到列宽，两侧各一个空格；
    - 分隔行 |----|：无空格，短横线数 = 列宽 + 2（最少 3 个），对齐冒号（:---:）保留。
  缺省格式化仓库 docs 目录下全部 .md；可用 -Paths 指定文件或目录。
  代码块（``` 围栏）内的内容不动。

.EXAMPLE
  pwsh tools/format-md.ps1
  pwsh tools/format-md.ps1 -Paths docs/实施手册.md
  pwsh tools/format-md.ps1 -Paths docs,README.md
#>
param(
    [Parameter(Position = 0)]
    [string[]]$Paths = @('docs')
)

$ErrorActionPreference = 'Stop'
# 路径相对当前目录解析：请在仓库根目录执行本脚本，或用 -Paths 传绝对路径

# 与 IDEA 对齐的宽度口径：东亚全角区段按 2 个宽度计，其余按 1
function Get-DisplayWidth([string]$s) {
    $w = 0
    foreach ($ch in $s.ToCharArray()) {
        $c = [int]$ch
        $full = ($c -ge 0x1100 -and $c -le 0x115F) -or
                ($c -ge 0x2E80 -and $c -le 0xA4CF) -or
                ($c -ge 0xAC00 -and $c -le 0xD7A3) -or
                ($c -ge 0xF900 -and $c -le 0xFAFF) -or
                ($c -ge 0xFE30 -and $c -le 0xFE4F) -or
                ($c -ge 0xFF00 -and $c -le 0xFF60) -or
                ($c -ge 0xFFE0 -and $c -le 0xFFE6)
        $w += if ($full) { 2 } else { 1 }
    }
    return $w
}

function Test-SeparatorCell([string]$cell) {
    return $cell -match '^:?-{3,}:?$'
}

function Format-MdTables([string]$path) {
    $lines = [System.IO.File]::ReadAllLines($path)
    $out = New-Object System.Collections.Generic.List[string]
    $inFence = $false
    $i = 0
    while ($i -lt $lines.Count) {
        $line = $lines[$i]
        if ($line -match '^\s*```') {
            $inFence = -not $inFence
            $out.Add($line)
            $i++
            continue
        }
        if (-not $inFence -and $line -match '^\s*\|.*\|\s*$') {
            # 收集连续表格行
            $block = New-Object System.Collections.Generic.List[string]
            while ($i -lt $lines.Count -and $lines[$i] -match '^\s*\|.*\|\s*$') {
                $block.Add($lines[$i])
                $i++
            }
            # 解析单元格
            $rows = New-Object System.Collections.Generic.List[object]
            foreach ($b in $block) {
                $t = $b.Trim()
                $t = $t.Substring(1, $t.Length - 2)
                $cells = @($t -split '(?<!\\)\|' | ForEach-Object { $_.Trim() })
                $rows.Add([pscustomobject]@{ Cells = $cells; Separator = @($cells | Where-Object { -not (Test-SeparatorCell $_) }).Count -eq 0 })
            }
            $cols = ($rows | ForEach-Object { $_.Cells.Count } | Measure-Object -Maximum).Maximum
            # 列宽 = 最大显示宽度；分隔线短横线数 = 列宽 + 2（最少 3）；内容补空格到列宽
            $widths = @()
            for ($c = 0; $c -lt $cols; $c++) {
                $max = 0
                foreach ($r in $rows) {
                    if ($r.Separator) { continue }
                    $cell = if ($c -lt $r.Cells.Count) { $r.Cells[$c] } else { '' }
                    $dw = Get-DisplayWidth $cell
                    if ($dw -gt $max) { $max = $dw }
                }
                $widths += $max
            }
            # 渲染
            foreach ($r in $rows) {
                if ($r.Separator) {
                    # 分隔行：|----|----|（无空格；短横线数 = 列宽 + 2，最少 3；对齐冒号保留）
                    $cells = New-Object System.Collections.Generic.List[string]
                    for ($c = 0; $c -lt $cols; $c++) {
                        $cell = if ($c -lt $r.Cells.Count) { $r.Cells[$c] } else { '' }
                        $n = $widths[$c] + 2
                        if ($n -lt 3) { $n = 3 }
                        if ($cell.StartsWith(':') -and $cell.EndsWith(':')) {
                            $cells.Add(':' + ('-' * ($n - 2)) + ':')
                        } else {
                            $cells.Add('-' * $n)
                        }
                    }
                    $out.Add('|' + ($cells -join '|') + '|')
                } else {
                    # 内容行：| 内容 |（单元格补空格到列宽，两侧各一个空格）
                    $cells = New-Object System.Collections.Generic.List[string]
                    for ($c = 0; $c -lt $cols; $c++) {
                        $cell = if ($c -lt $r.Cells.Count) { $r.Cells[$c] } else { '' }
                        $cells.Add($cell + (' ' * ($widths[$c] - (Get-DisplayWidth $cell))))
                    }
                    $out.Add('| ' + ($cells -join ' | ') + ' |')
                }
            }
        } else {
            $out.Add($line)
            $i++
        }
    }
    $content = ($out -join "`n") + "`n"
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
}

$targets = @()
foreach ($p in $Paths) {
    $item = Get-Item (Resolve-Path $p)
    if ($item.PSIsContainer) {
        $targets += Get-ChildItem $item.FullName -Recurse -Filter *.md | Select-Object -ExpandProperty FullName
    } else {
        $targets += $item.FullName
    }
}
$targets = @($targets | Sort-Object -Unique)
foreach ($t in $targets) {
    Format-MdTables $t
    Write-Host "FORMATTED: $t"
}
