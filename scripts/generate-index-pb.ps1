<#
.SYNOPSIS
  Stub for generating Mihon protobuf catalogue (index.pb).
.DESCRIPTION
  Phase 4 ships working legacy index.min.json. Do NOT write a fake index.pb.
#>
[CmdletBinding()]
param()

Write-Host @"
generate-index-pb.ps1 - NOT IMPLEMENTED (Phase 4 follow-up)

Legacy catalogue is ready:
  repo/index.min.json
  repo/index.json
  repo/repo.json

Add URL for AuroraReader:
  https://<host>/index.min.json

When implementing:
  - Use real protobuf encoding from tachiyomix index.proto
  - Never commit a placeholder/broken index.pb
"@

exit 2