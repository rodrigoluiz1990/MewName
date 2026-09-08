$assets=Join-Path $PSScriptRoot '../app/src/main/assets'
$tables=@{}
foreach($language in @('en','ptbr','es')) {
 $raw=[IO.File]::ReadAllText((Join-Path $assets "catalogs/text_$language.txt"))
 $table=@{}
 foreach($match in [regex]::Matches($raw,'RESOURCE ID:\s*move_name_(\d+)\s*TEXT:\s*(.*?)(?=\s*RESOURCE ID:|\z)',[Text.RegularExpressions.RegexOptions]::Singleline)) {
  $table[[int]$match.Groups[1].Value]=($match.Groups[2].Value -replace '\s+',' ').Trim()
 }
 $tables[$language]=$table
}
$labels=[ordered]@{}
foreach($id in ($tables.en.Keys | Sort-Object)) {
 $name=$tables.en[$id]
 $labels[$name]=[ordered]@{EN=$name;PT_BR=$tables.ptbr[$id];ES=$tables.es[$id]}
}
$seenNames=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($file in @('fast_moves.json','charged_moves.json')) {
 $entries=[IO.File]::ReadAllText((Join-Path $assets "catalogs/$file")) | ConvertFrom-Json
 foreach($move in ($entries | Sort-Object move_id)){
  if(!$seenNames.Add($move.name)){continue}
  $id=[int]$move.move_id
  $labels[$move.name]=[ordered]@{EN=$move.name;PT_BR=$tables.ptbr[$id];ES=$tables.es[$id]}
 }
}
foreach($key in @($labels.Keys)) {
 foreach($language in @('EN','PT_BR','ES')) {
  if([string]::IsNullOrWhiteSpace($labels[$key][$language])) { $labels[$key][$language]=$key }
 }
}
$json=ConvertTo-Json -InputObject $labels -Depth 3
[IO.File]::WriteAllText((Join-Path $assets 'catalogs/move_labels.json'),$json)
'Labels: '+$labels.Count