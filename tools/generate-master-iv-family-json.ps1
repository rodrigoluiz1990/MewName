param(
    [string]$WorkbookPath = "tools/source-data/Ranking PVP Master.xlsx",
    [string]$NamesPath = "app/src/main/assets/pokemon/names.json",
    [string]$FamiliesPath = "app/src/main/assets/pokemon/families.json",
    [string]$OutputPath = "app/src/main/assets/pvp/master_iv_table.json",
    [string]$LegacyOutputDir = "app/src/main/assets/pvp_master_iv_families"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Normalize-Text([string]$text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return "" }
    $normalized = $text.Normalize([Text.NormalizationForm]::FormD)
    $builder = New-Object System.Text.StringBuilder
    foreach ($char in $normalized.ToCharArray()) {
        if ([Globalization.CharUnicodeInfo]::GetUnicodeCategory($char) -ne [Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$builder.Append($char)
        }
    }
    return (($builder.ToString().ToUpperInvariant()) -replace "[^A-Z0-9]+", "")
}

function Build-VariantCandidates([string]$value) {
    $variants = [System.Collections.Generic.List[string]]::new()
    if ([string]::IsNullOrWhiteSpace($value)) { return $variants }
    $trimmed = $value.Trim()
    $variants.Add($trimmed)

    $cleaned = $trimmed `
        -replace "\s*\(Origem\)", " Origin" `
        -replace "\s*\(Origin Forme\)", " Origin" `
        -replace "\s*\(Altered Forme\)", " Altered" `
        -replace "\s*\(Defense Forme\)", " Defense" `
        -replace "\s*\(Attack Forme\)", " Attack" `
        -replace "\s*\(Speed Forme\)", " Speed" `
        -replace "\s*\(Land Forme\)", " Land" `
        -replace "\s*\(Sky Forme\)", " Sky" `
        -replace "\s*\(Encarnate\)", " Incarnate" `
        -replace "\s*\(Therian\)", " Therian" `
        -replace "\s*\(Hero\)", " Hero" `
        -replace "\s*\(Zero\)", " Zero" `
        -replace "\s*\(Rapid\)", " Rapid Strike" `
        -replace "\s*\(Single\)", " Single Strike" `
        -replace "\s*\(Ice\)", " Ice Rider" `
        -replace "\s*\(Shadow\)", " Shadow Rider" `
        -replace "\s*\(midday\)", " Midday" `
        -replace "\s*\(midnight\)", " Midnight"
    if ($cleaned -ne $trimmed) {
        $variants.Add($cleaned.Trim())
    }

    foreach ($region in @("Hisuian", "Galarian", "Alolan", "Paldean")) {
        if ($trimmed -match "^(?<name>.+?)\s+$region$") {
            $variants.Add("$region $($Matches.name)")
        }
    }

    foreach ($variant in @($variants)) {
        if ($variant -match "^(?<base>.+?)\s+\((?<form>.+)\)$") {
            $variants.Add("$($Matches.form) $($Matches.base)")
            $variants.Add("$($Matches.base) $($Matches.form)")
        }
    }

    return $variants | Select-Object -Unique
}

function Resolve-Species([string]$value, $manualSpeciesMap, $aliasMap) {
    foreach ($variant in (Build-VariantCandidates $value)) {
        $norm = Normalize-Text $variant
        if ($norm -and $aliasMap.ContainsKey($norm)) {
            return [pscustomobject]@{
                source = $value
                resolved = $aliasMap[$norm]
                mode = "exact"
            }
        }
        if ($norm -and $manualSpeciesMap.ContainsKey($norm)) {
            return [pscustomobject]@{
                source = $value
                resolved = $manualSpeciesMap[$norm]
                mode = "manual"
            }
        }
    }

    return [pscustomobject]@{
        source = $value
        resolved = $null
        mode = "unresolved"
    }
}

function Get-EntryText($zip, [string]$entryName) {
    $entry = $zip.Entries | Where-Object FullName -eq $entryName
    if (-not $entry) { return $null }
    $reader = New-Object IO.StreamReader($entry.Open())
    try {
        return $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
    }
}

function Get-CellValue($rowNode, [string]$cellRef, $sheetNs, $sharedStrings) {
    $cell = $rowNode.SelectSingleNode("./x:c[@r='$cellRef']", $sheetNs)
    if (-not $cell) { return $null }
    if ($cell.t -eq "s" -and $null -ne $cell.v) {
        return $sharedStrings[[int]$cell.v]
    }
    if ($cell.t -eq "inlineStr") {
        return ($cell.SelectNodes("./x:is/x:t", $sheetNs) | ForEach-Object { $_.'#text' }) -join ""
    }
    return $cell.v
}

function Convert-EntryToJsonLine($key, $combos) {
    $parts = @()
    $parts += '"key":"' + $key + '"'
    foreach ($ivPercent in @(98, 96, 93, 91, 67)) {
        $triplet = $combos[$ivPercent]
        $parts += '"' + $ivPercent + '":[' + ($triplet -join ",") + ']'
    }
    return "  {" + ($parts -join ",") + "}"
}

$workbookFullPath = (Resolve-Path $WorkbookPath).Path
$names = Get-Content $NamesPath -Raw | ConvertFrom-Json
$families = Get-Content $FamiliesPath -Raw | ConvertFrom-Json

$aliasMap = @{}
foreach ($entry in $names) {
    $allNames = @($entry.name) + @($entry.aliases)
    foreach ($candidate in $allNames | Select-Object -Unique) {
        $normalized = Normalize-Text $candidate
        if ($normalized -and -not $aliasMap.ContainsKey($normalized)) {
            $aliasMap[$normalized] = $entry.name
        }
    }
}

$manualSpeciesMap = @{
    (Normalize-Text "Bulbsaur") = "Bulbasaur"
    (Normalize-Text "Wartotle") = "Wartortle"
    (Normalize-Text "Baylef") = "Bayleef"
    (Normalize-Text "Corpish") = "Corphish"
    (Normalize-Text "Dewoot") = "Dewott"
    (Normalize-Text "Linone") = "Linoone"
    (Normalize-Text "Licktang") = "Lickitung"
    (Normalize-Text "Sneasael") = "Sneasel"
    (Normalize-Text "Wimpbell") = "Weepinbell"
    (Normalize-Text "Wurple") = "Wurmple"
    (Normalize-Text "Timbur") = "Timburr"
    (Normalize-Text "Vanilish") = "Vanillish"
    (Normalize-Text "Warmadam") = "Wormadam"
    (Normalize-Text "purrlion") = "Purrloin"
}

$familyBySpecies = @{}
foreach ($familyProperty in $families.PSObject.Properties) {
    $members = @($familyProperty.Value)
    foreach ($member in $members) {
        $familyBySpecies[(Normalize-Text $member)] = $members
    }
}

$zip = [IO.Compression.ZipFile]::OpenRead($workbookFullPath)
try {
    $sharedStringsXml = [xml](Get-EntryText $zip "xl/sharedStrings.xml")
    $sharedNs = New-Object System.Xml.XmlNamespaceManager($sharedStringsXml.NameTable)
    $sharedNs.AddNamespace("x", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
    $sharedStrings = @()
    foreach ($item in $sharedStringsXml.SelectNodes("//x:si", $sharedNs)) {
        $sharedStrings += ,(($item.SelectNodes(".//x:t", $sharedNs) | ForEach-Object { $_.'#text' }) -join "")
    }

    $sheetXml = [xml](Get-EntryText $zip "xl/worksheets/sheet1.xml")
    $sheetNs = New-Object System.Xml.XmlNamespaceManager($sheetXml.NameTable)
    $sheetNs.AddNamespace("x", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
    $rows = $sheetXml.SelectNodes("//x:sheetData/x:row[position()>1]", $sheetNs)

    $ivColumns = [ordered]@{
        98 = @("C", "D", "E")
        96 = @("F", "G", "H")
        93 = @("I", "J", "K")
        91 = @("L", "M", "N")
    }

    $records = New-Object System.Collections.Generic.List[string]
    $seenKeys = [System.Collections.Generic.HashSet[string]]::new()

    foreach ($row in $rows) {
        $rowNumber = [int]$row.r
        $familyLabel = Get-CellValue $row "B$rowNumber" $sheetNs $sharedStrings
        if ([string]::IsNullOrWhiteSpace($familyLabel)) { continue }

        $memberResults = @()
        foreach ($rawMember in ($familyLabel -split "/")) {
            $trimmed = $rawMember.Trim()
            if (-not $trimmed) { continue }
            $memberResults += Resolve-Species $trimmed $manualSpeciesMap $aliasMap
        }

        if ($memberResults.Count -eq 0) { continue }
        if (($memberResults | Where-Object { $_.mode -eq "unresolved" }).Count -gt 0) { continue }

        # Keep the row's lead species as the lookup key. Some families have split
        # Master IV rows (for example Vileplume and Bellossom), so collapsing to
        # the whole family would discard one valid branch.
        $signature = Normalize-Text $memberResults[0].resolved
        if ([string]::IsNullOrWhiteSpace($signature)) { continue }
        if (-not $seenKeys.Add($signature)) { continue }

        $combos = @{}
        $combos = @{}
        foreach ($entry in $ivColumns.GetEnumerator()) {
            $ivPercent = [int]$entry.Key
            $cols = $entry.Value
            $combos[$ivPercent] = @(
                [int](Get-CellValue $row "$($cols[0])$rowNumber" $sheetNs $sharedStrings),
                [int](Get-CellValue $row "$($cols[1])$rowNumber" $sheetNs $sharedStrings),
                [int](Get-CellValue $row "$($cols[2])$rowNumber" $sheetNs $sharedStrings)
            )
        }
        $combos[67] = @(10, 10, 10)

        $records.Add((Convert-EntryToJsonLine $signature $combos)) | Out-Null
    }

    $outputLines = @("[") + ($records | Sort-Object) + @("]")
    for ($index = 1; $index -lt ($outputLines.Count - 1); $index++) {
        if ($index -lt ($outputLines.Count - 2)) {
            $outputLines[$index] = $outputLines[$index] + ","
        }
    }

    Set-Content -Path $OutputPath -Value $outputLines -Encoding UTF8
    if (Test-Path $LegacyOutputDir) {
        Remove-Item -LiteralPath $LegacyOutputDir -Recurse -Force
    }
} finally {
    $zip.Dispose()
}
