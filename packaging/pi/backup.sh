#!/bin/sh
# Щоденний бекап бази. Додати:  crontab -e  →  0 3 * * * /opt/stock-app/backup.sh
set -e
SRC="$HOME/StockApp/data/stock.mv.db"
DST="$HOME/StockApp/backups"
[ -f "$SRC" ] || exit 0
mkdir -p "$DST"
cp "$SRC" "$DST/stock-$(date +%F).mv.db"
ls -1t "$DST"/*.mv.db | tail -n +31 | xargs -r rm --   # тримаємо 30 останніх
