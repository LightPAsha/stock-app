#!/bin/bash
# Збирає встановлювальний пакет stock-app_<версія>_all.deb
# Запуск з кореня проєкту:  ./packaging/pi/build-deb.sh
set -e
cd "$(dirname "$0")/../.."
VERSION=1.0.0

if [ ! -f target/stock-app.jar ]; then
  echo ">> Building jar (mvn package)…"
  mvn -q -B package
fi

PKG=target/deb/stock-app
rm -rf target/deb
mkdir -p $PKG/DEBIAN $PKG/opt/stock-app $PKG/usr/share/applications

cp target/stock-app.jar packaging/pi/splash.html packaging/pi/stock.png $PKG/opt/stock-app/
install -m 755 packaging/pi/stock-app $PKG/opt/stock-app/stock-app
install -m 755 packaging/pi/backup.sh $PKG/opt/stock-app/backup.sh
cp packaging/pi/stock-app.desktop $PKG/usr/share/applications/

cat > $PKG/DEBIAN/control <<EOF
Package: stock-app
Version: $VERSION
Section: misc
Priority: optional
Architecture: all
Depends: openjdk-17-jre | openjdk-21-jre | openjdk-17-jre-headless | openjdk-21-jre-headless | default-jre, curl
Recommends: chromium | chromium-browser
Maintainer: Stock App <stock@localhost>
Description: Spare parts stock
 Local spare parts inventory: sections, parts, +/−, history, Excel export.
EOF

# Після встановлення — іконка на робочому столі користувача
cat > $PKG/DEBIAN/postinst <<'EOF'
#!/bin/sh
set -e
U="${SUDO_USER:-$(logname 2>/dev/null || true)}"
if [ -n "$U" ] && [ "$U" != "root" ]; then
  H=$(getent passwd "$U" | cut -d: -f6)
  if [ -d "$H/Desktop" ]; then
    cp /usr/share/applications/stock-app.desktop "$H/Desktop/"
    chown "$U": "$H/Desktop/stock-app.desktop"
    chmod +x "$H/Desktop/stock-app.desktop"
  fi
fi
command -v update-desktop-database >/dev/null && update-desktop-database -q || true
exit 0
EOF
chmod 755 $PKG/DEBIAN/postinst

dpkg-deb --build --root-owner-group $PKG "target/stock-app_${VERSION}_all.deb"
echo
echo "Done: target/stock-app_${VERSION}_all.deb"
echo "Install:  sudo apt install ./target/stock-app_${VERSION}_all.deb"
