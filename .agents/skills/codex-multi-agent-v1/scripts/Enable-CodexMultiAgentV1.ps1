[CmdletBinding()]
param(
    [string]$CodexHome,
    [string]$SourceCatalogPath,
    [string]$TargetCatalogPath,
    [switch]$RefreshFromCache,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$targetSlugs = @('gpt-5.6-sol', 'gpt-5.6-terra', 'gpt-5.6-luna')
$requiredSlugs = @('gpt-5.6-sol', 'gpt-5.6-terra')
$utf8NoBom = [Text.UTF8Encoding]::new($false)

function Get-FileSha([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    }
    return $null
}

function Get-AbsolutePath([string]$Path, [string]$BasePath) {
    $expandedPath = [Environment]::ExpandEnvironmentVariables($Path)
    if (-not [IO.Path]::IsPathRooted($expandedPath)) {
        $expandedPath = Join-Path $BasePath $expandedPath
    }
    return [IO.Path]::GetFullPath($expandedPath)
}

function Get-TopLevelCatalogPath([string]$ConfigText) {
    $foundValues = @()
    foreach ($line in ($ConfigText -split "`r?`n")) {
        if ($line -match '^\s*\[') {
            break
        }
        if ($line -match '^\s*model_catalog_json\s*=\s*(["''])(.*?)\1\s*(?:#.*)?$') {
            $foundValues += $Matches[2]
        }
    }
    if ($foundValues.Count -gt 1) {
        throw 'Duplicate top-level model_catalog_json keys in config.toml.'
    }
    if ($foundValues.Count -eq 1) {
        return $foundValues[0]
    }
    return $null
}

function Set-TopLevelCatalogPath([string]$ConfigText, [string]$CatalogPath) {
    $newline = if ($ConfigText.Contains("`r`n")) { "`r`n" } else { "`n" }
    $hadTrailingNewline = $ConfigText.EndsWith("`n")
    $configLines = @($ConfigText -split "`r?`n")
    if ($hadTrailingNewline -and $configLines.Count -gt 1 -and $configLines[-1] -eq '') {
        $configLines = @($configLines[0..($configLines.Count - 2)])
    }

    $firstTableIndex = $configLines.Count
    $catalogKeyIndexes = @()
    for ($index = 0; $index -lt $configLines.Count; $index++) {
        if ($configLines[$index] -match '^\s*\[') {
            $firstTableIndex = $index
            break
        }
        if ($configLines[$index] -match '^\s*model_catalog_json\s*=') {
            $catalogKeyIndexes += $index
        }
    }
    if ($catalogKeyIndexes.Count -gt 1) {
        throw 'Duplicate top-level model_catalog_json keys in config.toml.'
    }

    $literalPath = "'" + $CatalogPath.Replace("'", "''") + "'"
    $catalogLine = "model_catalog_json = $literalPath"
    if ($catalogKeyIndexes.Count -eq 1) {
        $configLines[$catalogKeyIndexes[0]] = $catalogLine
    } elseif ($firstTableIndex -eq 0) {
        $configLines = @($catalogLine) + $configLines
    } elseif ($firstTableIndex -ge $configLines.Count) {
        $configLines = $configLines + @($catalogLine)
    } else {
        $configLines = @($configLines[0..($firstTableIndex - 1)]) +
            @($catalogLine) +
            @($configLines[$firstTableIndex..($configLines.Count - 1)])
    }

    $updatedText = [string]::Join($newline, $configLines)
    if ($hadTrailingNewline) {
        $updatedText += $newline
    }
    return $updatedText
}

function Write-AtomicText([string]$Path, [string]$Text) {
    $parentPath = Split-Path -Parent $Path
    $temporaryPath = Join-Path $parentPath ('.' + [IO.Path]::GetFileName($Path) + '.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($temporaryPath, $Text, $utf8NoBom)
        [IO.File]::Move($temporaryPath, $Path, $true)
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

if ([string]::IsNullOrWhiteSpace($CodexHome)) {
    if (-not [string]::IsNullOrWhiteSpace($env:CODEX_HOME)) {
        $CodexHome = $env:CODEX_HOME
    } elseif (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $CodexHome = Join-Path $env:USERPROFILE '.codex'
    } else {
        throw 'Provide -CodexHome or set CODEX_HOME/USERPROFILE.'
    }
}
$CodexHome = Get-AbsolutePath $CodexHome (Get-Location).Path
if (-not (Test-Path -LiteralPath $CodexHome -PathType Container)) {
    throw "Codex home does not exist: $CodexHome"
}

$configPath = Join-Path $CodexHome 'config.toml'
$cachePath = Join-Path $CodexHome 'models_cache.json'
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw "config.toml does not exist: $configPath"
}
$configText = [IO.File]::ReadAllText($configPath)
$configShaBefore = Get-FileSha $configPath

if ([string]::IsNullOrWhiteSpace($TargetCatalogPath)) {
    $TargetCatalogPath = Join-Path $CodexHome 'models-v1.json'
} else {
    $TargetCatalogPath = Get-AbsolutePath $TargetCatalogPath $CodexHome
}

if (-not [string]::IsNullOrWhiteSpace($SourceCatalogPath)) {
    $SourceCatalogPath = Get-AbsolutePath $SourceCatalogPath $CodexHome
} elseif ($RefreshFromCache) {
    $SourceCatalogPath = $cachePath
} else {
    $configuredPath = Get-TopLevelCatalogPath $configText
    if (-not [string]::IsNullOrWhiteSpace($configuredPath)) {
        $configuredPath = Get-AbsolutePath $configuredPath $CodexHome
    }
    if ($configuredPath -and (Test-Path -LiteralPath $configuredPath -PathType Leaf)) {
        $SourceCatalogPath = $configuredPath
    } else {
        $SourceCatalogPath = $cachePath
    }
}
if (-not (Test-Path -LiteralPath $SourceCatalogPath -PathType Leaf)) {
    throw "Source model catalog does not exist: $SourceCatalogPath"
}

$sourceShaBefore = Get-FileSha $SourceCatalogPath
$sourceText = [IO.File]::ReadAllText($SourceCatalogPath)
$sourceRoot = $sourceText | ConvertFrom-Json -AsHashtable
$candidateRoot = $sourceText | ConvertFrom-Json -AsHashtable
if (-not $sourceRoot.ContainsKey('models') -or $sourceRoot['models'] -isnot [Collections.IList]) {
    throw "Source catalog must contain a top-level 'models' array."
}

$sourceBySlug = @{}
$candidateBySlug = @{}
foreach ($slug in $targetSlugs) {
    $sourceMatches = @($sourceRoot['models'] | Where-Object { $_['slug'] -ceq $slug })
    $candidateMatches = @($candidateRoot['models'] | Where-Object { $_['slug'] -ceq $slug })
    if ($sourceMatches.Count -gt 1) {
        throw "Duplicate model slug in source catalog: $slug"
    }
    if ($sourceMatches.Count -eq 1) {
        $sourceBySlug[$slug] = $sourceMatches[0]
        $candidateBySlug[$slug] = $candidateMatches[0]
    }
}
foreach ($slug in $requiredSlugs) {
    if (-not $sourceBySlug.ContainsKey($slug)) {
        throw "Required model is missing from source catalog: $slug"
    }
}
foreach ($slug in $targetSlugs) {
    if ($candidateBySlug.ContainsKey($slug)) {
        $candidateBySlug[$slug]['multi_agent_version'] = 'v1'
    }
}

$comparisonRoot = ($candidateRoot | ConvertTo-Json -Depth 100 -Compress) | ConvertFrom-Json -AsHashtable
foreach ($slug in $targetSlugs) {
    if (-not $sourceBySlug.ContainsKey($slug)) {
        continue
    }
    $comparisonModel = @($comparisonRoot['models'] | Where-Object { $_['slug'] -ceq $slug })[0]
    if ($sourceBySlug[$slug].ContainsKey('multi_agent_version')) {
        $comparisonModel['multi_agent_version'] = $sourceBySlug[$slug]['multi_agent_version']
    } else {
        $comparisonModel.Remove('multi_agent_version') | Out-Null
    }
}
if (($sourceRoot | ConvertTo-Json -Depth 100 -Compress) -cne
    ($comparisonRoot | ConvertTo-Json -Depth 100 -Compress)) {
    throw 'Candidate changed fields outside the allowed multi_agent_version leaves.'
}

$candidateText = ($candidateRoot | ConvertTo-Json -Depth 100) + "`n"
$updatedConfigText = Set-TopLevelCatalogPath $configText $TargetCatalogPath
$summary = [ordered]@{
    codex_home = $CodexHome
    config = $configPath
    source_catalog = $SourceCatalogPath
    target_catalog = $TargetCatalogPath
    dry_run = [bool]$DryRun
    present_targets = @($targetSlugs | Where-Object { $sourceBySlug.ContainsKey($_) })
    changed_targets = @($targetSlugs | Where-Object {
        $sourceBySlug.ContainsKey($_) -and $sourceBySlug[$_]['multi_agent_version'] -cne 'v1'
    })
    config_change = ($updatedConfigText -cne $configText)
}
if ($DryRun) {
    $summary | ConvertTo-Json -Depth 10
    return
}

if ((Get-FileSha $SourceCatalogPath) -cne $sourceShaBefore) {
    throw "Source catalog changed during operation: $SourceCatalogPath"
}
if ((Get-FileSha $configPath) -cne $configShaBefore) {
    throw "config.toml changed during operation: $configPath"
}

$targetExisted = Test-Path -LiteralPath $TargetCatalogPath -PathType Leaf
$oldTargetText = if ($targetExisted) { [IO.File]::ReadAllText($TargetCatalogPath) } else { $null }
$writeTarget = -not $targetExisted -or $oldTargetText -cne $candidateText
$writeConfig = $updatedConfigText -cne $configText
$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmssfff')
$targetBackup = $null
$configBackup = $null

try {
    if ($writeTarget) {
        if ($targetExisted) {
            $targetBackup = "$TargetCatalogPath.bak-$stamp"
            Copy-Item -LiteralPath $TargetCatalogPath -Destination $targetBackup
        }
        Write-AtomicText $TargetCatalogPath $candidateText
    }
    if ($writeConfig) {
        $configBackup = "$configPath.bak-$stamp"
        Copy-Item -LiteralPath $configPath -Destination $configBackup
        Write-AtomicText $configPath $updatedConfigText
    }
} catch {
    if ($configBackup -and (Test-Path -LiteralPath $configBackup)) {
        Copy-Item -LiteralPath $configBackup -Destination $configPath -Force
    }
    if ($targetBackup -and (Test-Path -LiteralPath $targetBackup)) {
        Copy-Item -LiteralPath $targetBackup -Destination $TargetCatalogPath -Force
    } elseif (-not $targetExisted -and (Test-Path -LiteralPath $TargetCatalogPath)) {
        Remove-Item -LiteralPath $TargetCatalogPath -Force
    }
    throw
}

$writtenRoot = [IO.File]::ReadAllText($TargetCatalogPath) | ConvertFrom-Json -AsHashtable
foreach ($slug in $requiredSlugs) {
    $writtenMatches = @($writtenRoot['models'] | Where-Object { $_['slug'] -ceq $slug })
    if ($writtenMatches.Count -ne 1 -or $writtenMatches[0]['multi_agent_version'] -cne 'v1') {
        throw "Post-write validation failed for $slug"
    }
}
if ($sourceBySlug.ContainsKey('gpt-5.6-luna')) {
    $writtenLuna = @($writtenRoot['models'] | Where-Object { $_['slug'] -ceq 'gpt-5.6-luna' })
    if ($writtenLuna.Count -ne 1 -or $writtenLuna[0]['multi_agent_version'] -cne 'v1') {
        throw 'Post-write validation failed for gpt-5.6-luna'
    }
}

$effectiveCatalog = Get-TopLevelCatalogPath ([IO.File]::ReadAllText($configPath))
$effectiveCatalog = Get-AbsolutePath $effectiveCatalog $CodexHome
if (-not [StringComparer]::OrdinalIgnoreCase.Equals($effectiveCatalog, $TargetCatalogPath)) {
    throw 'Post-write config validation failed: model_catalog_json does not point to target.'
}

$summary['catalog_written'] = $writeTarget
$summary['config_written'] = $writeConfig
$summary['catalog_backup'] = $targetBackup
$summary['config_backup'] = $configBackup
$summary | ConvertTo-Json -Depth 10
