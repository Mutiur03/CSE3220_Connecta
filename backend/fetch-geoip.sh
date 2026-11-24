#!/bin/sh
# Fetches the city database for session locations. Never fails the build: no file just means no location.
# Usage: fetch-geoip.sh <dest> [url]
#   no url  -> DB-IP City Lite (free, CC BY 4.0, https://db-ip.com), this month's file then last month's
#   a url   -> that .mmdb or .mmdb.gz (for example your own MaxMind download)
#   "none"  -> skip
dest=$1
url=$2
[ "$url" = "none" ] && exit 0

fetch() {
  wget -q -O "$dest.dl" "$1" || { rm -f "$dest.dl"; return 1; }
  case "$1" in
    *.gz) gunzip -c "$dest.dl" > "$dest" || { rm -f "$dest" "$dest.dl"; return 1; } ; rm -f "$dest.dl" ;;
    *) mv "$dest.dl" "$dest" ;;
  esac
  [ -s "$dest" ]
}

if [ -n "$url" ]; then
  fetch "$url" && exit 0
else
  year=$(date -u +%Y)
  month=$(date -u +%m)
  prev_month=$((${month#0} - 1))
  prev_year=$year
  if [ "$prev_month" -eq 0 ]; then prev_month=12; prev_year=$((year - 1)); fi
  prev=$(printf '%s-%02d' "$prev_year" "$prev_month")
  for stamp in "$year-$month" "$prev"; do
    fetch "https://download.db-ip.com/free/dbip-city-lite-$stamp.mmdb.gz" && exit 0
  done
fi
echo "GeoIP download failed, continuing without it"
exit 0
