Как получить .jar (бесплатно, без установки программ):
1. Зарегистрируйся на github.com
2. Нажми "+" -> New repository, назови icemage, создай.
3. Нажми "uploading an existing file", перетащи ВСЁ содержимое этой папки
   (включая скрытую папку .github, если её не видно - см. ответ в чате), Commit changes.
4. Вкладка Actions -> дождись зелёной галочки (2-4 минуты).
5. Открой завершённую сборку -> внизу Artifacts -> скачай icemage-jar.
6. Распакуй zip, внутри будет icemage-1.0.0.jar.

Aternos: Software = Fabric 1.21.1. В папку mods загрузи icemage-1.0.0.jar и Fabric API 0.105.0+1.21.1.
В игре (нужен op):
/give @s blaze_rod[custom_name='{"text":"Ледяной посох","color":"aqua","italic":false}',custom_data={ice_staff:1b}]
