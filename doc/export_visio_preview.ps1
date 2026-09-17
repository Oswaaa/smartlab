param(
    [Parameter(Mandatory = $true)]
    [string]$VisioPath,

    [Parameter(Mandatory = $true)]
    [string]$PreviewPath
)

$ErrorActionPreference = 'Stop'
$visio = $null
$document = $null
$page = $null

try {
    $visio = New-Object -ComObject Visio.InvisibleApp
    $visio.AlertResponse = 7
    $visio.EventsEnabled = 0
    $document = $visio.Documents.Open($VisioPath)
    $page = $document.Pages.Item(1)
    $page.Export($PreviewPath)
    Write-Output ('EXPORTED_PREVIEW={0}' -f $PreviewPath)
    Write-Output ('SHAPES={0}' -f $page.Shapes.Count)
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
