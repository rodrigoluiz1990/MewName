param(
    [string]$ManifestPath = "tools/vivillon-validation/samples.csv",
    [string]$ScreenshotDir = "tools/vivillon-validation/screenshots",
    [string]$OutputDir = "tools/vivillon-validation/crops"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function Get-FieldOrDefault($row, [string]$name, [double]$defaultValue) {
    $value = $row.$name
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $defaultValue
    }
    return [double]::Parse($value, [Globalization.CultureInfo]::InvariantCulture)
}

function Normalize-Pattern([string]$pattern) {
    if ([string]::IsNullOrWhiteSpace($pattern)) {
        return "unknown"
    }
    return $pattern.Trim().ToLowerInvariant() -replace "[^a-z0-9]+", "_"
}

if (!(Test-Path $ManifestPath)) {
    throw "Manifesto nao encontrado: $ManifestPath"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$rows = Import-Csv $ManifestPath
foreach ($row in $rows) {
    if ([string]::IsNullOrWhiteSpace($row.file) -or $row.file.StartsWith("exemplo")) {
        continue
    }

    $inputPath = Join-Path $ScreenshotDir $row.file
    if (!(Test-Path $inputPath)) {
        Write-Warning "Screenshot nao encontrada: $inputPath"
        continue
    }

    $bitmap = [System.Drawing.Bitmap]::FromFile((Resolve-Path $inputPath))
    try {
        $leftRatio = Get-FieldOrDefault $row "crop_left" 0.165
        $topRatio = Get-FieldOrDefault $row "crop_top" 0.790
        $rightRatio = Get-FieldOrDefault $row "crop_right" 0.250
        $bottomRatio = Get-FieldOrDefault $row "crop_bottom" 0.855

        $left = [Math]::Max(0, [Math]::Floor($bitmap.Width * $leftRatio))
        $top = [Math]::Max(0, [Math]::Floor($bitmap.Height * $topRatio))
        $right = [Math]::Min($bitmap.Width, [Math]::Ceiling($bitmap.Width * $rightRatio))
        $bottom = [Math]::Min($bitmap.Height, [Math]::Ceiling($bitmap.Height * $bottomRatio))
        $width = [Math]::Max(1, $right - $left)
        $height = [Math]::Max(1, $bottom - $top)

        $rect = [System.Drawing.Rectangle]::new($left, $top, $width, $height)
        $crop = $bitmap.Clone($rect, $bitmap.PixelFormat)
        try {
            $baseName = [IO.Path]::GetFileNameWithoutExtension($row.file)
            $pattern = Normalize-Pattern $row.expected_pattern
            $outputPath = Join-Path $OutputDir "$pattern-$baseName.png"
            $crop.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output "crop: $outputPath"
        } finally {
            $crop.Dispose()
        }
    } finally {
        $bitmap.Dispose()
    }
}
