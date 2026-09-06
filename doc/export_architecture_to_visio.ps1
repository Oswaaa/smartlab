param(
    [Parameter(Mandatory = $true)]
    [string]$SvgPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

[xml]$svg = Get-Content -Raw -LiteralPath $SvgPath
$root = $svg.DocumentElement
$canvasWidth = [double]$root.viewBox.Split(' ')[2]
$canvasHeight = [double]$root.viewBox.Split(' ')[3]
$pixelsPerInch = 100.0

function To-X([double]$value) {
    return $value / $pixelsPerInch
}

function To-Y([double]$value) {
    return ($canvasHeight - $value) / $pixelsPerInch
}

function Set-No-Line($shape) {
    $shape.CellsU('LinePattern').FormulaU = '0'
}

function Set-No-Fill($shape) {
    $shape.CellsU('FillPattern').FormulaU = '0'
}

function Set-Line-Style($shape, [string]$className) {
    $shape.CellsU('LineColor').FormulaU = 'RGB(25,25,25)'
    $shape.CellsU('LineWeight').FormulaU = '0.010 in'
    if ($className -in @('dashed', 'data', 'data-flow', 'persist-flow', 'db-zone', 'external', 'mechanism-zone', 'landing-zone', 'mechanism-flow', 'landing-flow', 'feedback-flow')) {
        $shape.CellsU('LinePattern').FormulaU = '2'
    }
    else {
        $shape.CellsU('LinePattern').FormulaU = '1'
    }
}

function Set-Rect-Style($shape, [string]$className) {
    if ($className -in @('label-bg', 'background')) {
        Set-No-Line $shape
        $shape.CellsU('FillPattern').FormulaU = '1'
        $shape.CellsU('FillForegnd').FormulaU = 'RGB(255,255,255)'
        return
    }

    if ($className -eq 'person-fill') {
        Set-No-Line $shape
        $shape.CellsU('FillPattern').FormulaU = '1'
        $shape.CellsU('FillForegnd').FormulaU = 'RGB(68,68,68)'
        return
    }

    Set-Line-Style $shape $className
    $shape.CellsU('FillPattern').FormulaU = '1'
    if ($className -in @('soft', 'smart-pool', 'runtime-pool', 'service-item', 'role', 'cylinder-cap', 'external')) {
        $shape.CellsU('FillForegnd').FormulaU = 'RGB(247,247,247)'
    }
    else {
        $shape.CellsU('FillForegnd').FormulaU = 'RGB(255,255,255)'
    }

    if ($className -in @('outer', 'runtime-pool')) {
        $shape.CellsU('LineWeight').FormulaU = '0.014 in'
    }
}

function Get-Font-Pixels([string]$className) {
    switch ($className) {
        'title' { return 27.0 }
        'subtitle' { return 14.0 }
        'module-title' { return 20.0 }
        'module-subtitle' { return 13.0 }
        'item-title' { return 16.0 }
        'zone-title' { return 18.0 }
        'stage-title' { return 16.0 }
        'stage-title-small' { return 14.0 }
        'group-title' { return 20.0 }
        'section-title' { return 17.0 }
        'label' { return 15.0 }
        'small' { return 13.0 }
        'tiny' { return 12.0 }
        'edge-label' { return 12.5 }
        default { return 13.0 }
    }
}

function Get-Text-Width([string]$text, [double]$fontPixels) {
    $units = 0.0
    foreach ($character in $text.ToCharArray()) {
        if ([int]$character -gt 255) {
            $units += 1.18
        }
        elseif ([char]::IsWhiteSpace($character)) {
            $units += 0.42
        }
        elseif ([char]::IsUpper($character)) {
            $units += 0.76
        }
        else {
            $units += 0.64
        }
    }
    return [Math]::Max(30.0, ($units * $fontPixels) + 24.0)
}

function Add-Text-Shape($page, $node) {
    $className = [string]$node.class
    $fontPixels = Get-Font-Pixels $className
    $text = [string]$node.InnerText
    $x = [double]$node.x
    $baselineY = [double]$node.y
    $widthPixels = Get-Text-Width $text $fontPixels
    $heightPixels = $fontPixels * 1.55
    $anchor = [string]$node.'text-anchor'

    if ($anchor -eq 'end') {
        $left = $x - $widthPixels
    }
    elseif ($anchor -eq 'middle') {
        $left = $x - ($widthPixels / 2.0)
    }
    else {
        $left = $x
    }

    $top = $baselineY - ($fontPixels * 1.18)
    $right = $left + $widthPixels
    $bottom = $top + $heightPixels

    $shape = $page.DrawRectangle((To-X $left), (To-Y $bottom), (To-X $right), (To-Y $top))
    Set-No-Line $shape
    Set-No-Fill $shape
    $shape.Text = $text
    $shape.CellsU('Para.HorzAlign').FormulaU = '1'
    $shape.CellsU('VerticalAlign').FormulaU = '1'
    $shape.CellsU('Char.Size').FormulaU = ('{0} pt' -f [Math]::Round($fontPixels * 0.68, 2))
    $shape.CellsU('Char.Color').FormulaU = 'RGB(17,17,17)'
    if ($script:visioFontId -ne $null) {
        $shape.CellsU('Char.Font').FormulaU = [string]$script:visioFontId
    }
    $shape.CellsU('LeftMargin').FormulaU = '0.01 in'
    $shape.CellsU('RightMargin').FormulaU = '0.01 in'
    $shape.CellsU('TopMargin').FormulaU = '0 in'
    $shape.CellsU('BottomMargin').FormulaU = '0 in'
    if ($className -in @('title', 'group-title', 'section-title', 'module-title', 'item-title', 'zone-title', 'stage-title', 'stage-title-small', 'edge-label')) {
        $shape.CellsU('Char.Style').FormulaU = '1'
    }
    return $shape
}

function Get-Path-Points([string]$pathData) {
    $tokens = [regex]::Matches($pathData, '[MHV]|-?\d+(?:\.\d+)?') | ForEach-Object { $_.Value }
    $points = New-Object System.Collections.Generic.List[object]
    $index = 0
    $x = 0.0
    $y = 0.0

    while ($index -lt $tokens.Count) {
        $command = $tokens[$index]
        $index++
        switch ($command) {
            'M' {
                $x = [double]$tokens[$index]
                $y = [double]$tokens[$index + 1]
                $index += 2
                $points.Add([pscustomobject]@{ X = $x; Y = $y })
            }
            'H' {
                $x = [double]$tokens[$index]
                $index++
                $points.Add([pscustomobject]@{ X = $x; Y = $y })
            }
            'V' {
                $y = [double]$tokens[$index]
                $index++
                $points.Add([pscustomobject]@{ X = $x; Y = $y })
            }
        }
    }
    return $points
}

function Add-Path-Shapes($page, $node) {
    $className = [string]$node.class
    $points = Get-Path-Points ([string]$node.d)
    if ($points.Count -lt 2) {
        return @()
    }

    $segments = New-Object System.Collections.Generic.List[object]
    for ($i = 0; $i -lt ($points.Count - 1); $i++) {
        $start = $points[$i]
        $finish = $points[$i + 1]
        $line = $page.DrawLine((To-X $start.X), (To-Y $start.Y), (To-X $finish.X), (To-Y $finish.Y))
        Set-Line-Style $line $className
        if ($className -eq 'leader') {
            $line.CellsU('LinePattern').FormulaU = '2'
            $line.CellsU('LineColor').FormulaU = 'RGB(70,70,70)'
        }
        $segments.Add($line)
    }

    if ($className -in @('flow', 'flow-bi', 'data')) {
        $segments[$segments.Count - 1].CellsU('EndArrow').FormulaU = '13'
        $segments[$segments.Count - 1].CellsU('EndArrowSize').FormulaU = '2'
    }
    if ($className -eq 'flow-bi') {
        $segments[0].CellsU('BeginArrow').FormulaU = '13'
        $segments[0].CellsU('BeginArrowSize').FormulaU = '2'
    }
    return $segments
}

function Get-Polyline-Points([string]$pointData) {
    $numbers = [regex]::Matches($pointData, '-?\d+(?:\.\d+)?') | ForEach-Object { [double]$_.Value }
    $points = New-Object System.Collections.Generic.List[object]
    for ($i = 0; $i -lt ($numbers.Count - 1); $i += 2) {
        $points.Add([pscustomobject]@{ X = $numbers[$i]; Y = $numbers[$i + 1] })
    }
    return $points
}

function Add-Polyline-Shapes($page, $node) {
    $className = [string]$node.class
    $points = Get-Polyline-Points ([string]$node.points)
    if ($points.Count -lt 2) {
        return @()
    }

    $segments = New-Object System.Collections.Generic.List[object]
    for ($i = 0; $i -lt ($points.Count - 1); $i++) {
        $start = $points[$i]
        $finish = $points[$i + 1]
        $line = $page.DrawLine((To-X $start.X), (To-Y $start.Y), (To-X $finish.X), (To-Y $finish.Y))
        Set-Line-Style $line $className
        $segments.Add($line)
    }

    $segments[$segments.Count - 1].CellsU('EndArrow').FormulaU = '13'
    $segments[$segments.Count - 1].CellsU('EndArrowSize').FormulaU = '2'
    if ($node.GetAttribute('marker-start')) {
        $segments[0].CellsU('BeginArrow').FormulaU = '13'
        $segments[0].CellsU('BeginArrowSize').FormulaU = '2'
    }
    return $segments
}

$visio = $null
$document = $null
try {
    $visio = New-Object -ComObject Visio.InvisibleApp
    $visio.AlertResponse = 7
    $visio.EventsEnabled = 0
    $document = $visio.Documents.Add('')
    $page = $visio.ActivePage
    try {
        $script:visioFontId = $document.Fonts.Item('Microsoft YaHei').ID
    }
    catch {
        $script:visioFontId = $null
    }
    $page.Name = 'SmartLab 2.0 系统结构图'
    $page.PageSheet.CellsU('PageWidth').FormulaU = ('{0} in' -f ($canvasWidth / $pixelsPerInch))
    $page.PageSheet.CellsU('PageHeight').FormulaU = ('{0} in' -f ($canvasHeight / $pixelsPerInch))
    $page.PageSheet.CellsU('PrintPageOrientation').FormulaU = '2'

    $created = New-Object System.Collections.Generic.List[object]
    foreach ($node in $root.ChildNodes) {
        if ($node.LocalName -eq 'rect') {
            if ([string]$node.class -eq '' -and [double]$node.width -eq $canvasWidth -and [double]$node.height -eq $canvasHeight) {
                continue
            }
            $x = [double]$node.x
            $y = [double]$node.y
            $width = [double]$node.width
            $height = [double]$node.height
            $shape = $page.DrawRectangle((To-X $x), (To-Y ($y + $height)), (To-X ($x + $width)), (To-Y $y))
            Set-Rect-Style $shape ([string]$node.class)
            $created.Add($shape)
        }
        elseif ($node.LocalName -eq 'ellipse') {
            $cx = [double]$node.cx
            $cy = [double]$node.cy
            $rx = [double]$node.rx
            $ry = [double]$node.ry
            $shape = $page.DrawOval((To-X ($cx - $rx)), (To-Y ($cy + $ry)), (To-X ($cx + $rx)), (To-Y ($cy - $ry)))
            Set-Rect-Style $shape ([string]$node.class)
            $created.Add($shape)
        }
        elseif ($node.LocalName -eq 'text') {
            $created.Add((Add-Text-Shape $page $node))
        }
        elseif ($node.LocalName -eq 'polyline') {
            foreach ($line in (Add-Polyline-Shapes $page $node)) {
                $created.Add($line)
            }
        }
        elseif ($node.LocalName -eq 'path') {
            foreach ($line in (Add-Path-Shapes $page $node)) {
                $created.Add($line)
            }
        }
    }

    $document.SaveAs($OutputPath)
    Write-Output ('CREATED_SHAPES={0}' -f $created.Count)
    Write-Output ('PAGE_SIZE={0}x{1}in' -f $page.PageSheet.CellsU('PageWidth').ResultIU, $page.PageSheet.CellsU('PageHeight').ResultIU)
}
finally {
    if ($document) {
        $document.Saved = $true
        $document.Close()
    }
    if ($visio) {
        $visio.Quit()
    }
    foreach ($object in @($page, $document, $visio)) {
        if ($object) {
            [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($object)
        }
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
