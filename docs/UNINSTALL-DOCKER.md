# 停止并卸载旧汐序新闻 Docker

适用旧 `hit-daily` v3.x / v4.0 项目。汐序 0.7.0 已由手机通过校园 VPN 获取消息；先在手机填好模型资料并完成一次“立即获取”，再停止服务器上的旧新闻服务。

以下命令仅针对原来的 `hit-daily` Compose 项目，不卸载 Docker，也不操作 MaiBot、SearXNG 或其他项目。

## 1. 进入原安装目录，确认项目

SSH 登录你的服务器，在 Bash 中进入原来的 `hit-daily` 目录。例如原来安装在 `/opt/hit-daily`，才使用这一行；实际路径不同就换成自己的路径：

```bash
cd /opt/hit-daily
pwd
```

目录内应有 `compose.yaml`、`config.toml` 和 `.env`。不要在新下载的源码目录或其他程序的目录里卸载。

原来使用随包 Caddy 的公网 IP / HTTPS 方案，执行：

```bash
news_compose=(sudo docker compose -f compose.yaml -f compose.https.yaml)
"${news_compose[@]}" ps -a
```

如果原来用的是 `start.sh --existing-proxy`、复用自己的 HTTPS 反向代理，改用下面这组，二选一：

```bash
news_compose=(sudo docker compose -f compose.yaml)
"${news_compose[@]}" ps -a
```

确认列出的服务是 `hit-daily`，以及默认方案中的随包 `caddy`。若曾自行设置 `-p 项目名` 或 `COMPOSE_PROJECT_NAME`，必须沿用同一个项目名；不能用另一个项目名执行下面的命令。

后续命令继续在同一个终端窗口执行，以保留上面的 `news_compose` 变量。重新连接终端后，请先重新执行对应的一组。

## 2. 可选：备份历史与配置

App 只保留已经接收的本机新闻缓存，最多 90 份，并非完整服务器历史，也不会自动导入下面的数据库备份。需要保留全部历史或回退能力时，在旧容器仍正常运行时执行本节。

这组命令通过 SQLite 的备份接口保存当前数据库快照，不触发学校抓取或模型调用。它将备份放在原安装目录旁边；只有全部步骤完成才显示“备份完成”。

```bash
(
  set -e
  news_backup_dir="../hit-daily-backup-$(date +%Y%m%d-%H%M%S)"
  mkdir -m 700 -- "$news_backup_dir"
  "${news_compose[@]}" exec -T hit-daily python - <<'PY'
import sqlite3
with sqlite3.connect("file:/data/state.sqlite3?mode=ro", uri=True) as source:
    with sqlite3.connect("/data/uninstall-backup.sqlite3") as saved:
        source.backup(saved)
        if saved.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise SystemExit("数据库备份校验失败，停止")
PY
  "${news_compose[@]}" cp hit-daily:/data/uninstall-backup.sqlite3 "$news_backup_dir/state.sqlite3"
  for news_file in .env config.toml compose.yaml compose.https.yaml Caddyfile; do
    if [[ -f "$news_file" ]]; then
      sudo cp -p -- "$news_file" "$news_backup_dir/"
    fi
  done
  sudo chmod -R go-rwx -- "$news_backup_dir"
  echo "备份完成：$news_backup_dir"
)
```

备份中含模型密钥和旧接口密钥，请仅保存在自己的设备中。若命令报错，不执行下一节的彻底删除；保留数据卷即可稍后处理。快照之后仍在运行中的精选结果不包含在这份快照内。

## 3. 推荐：停止服务，保留数据

```bash
"${news_compose[@]}" down --timeout 150
"${news_compose[@]}" ps -a
```

此时该项目不再运行定时精选，也不再提供旧新闻接口。随包 Caddy 方案会同时停止这个项目内的 Caddy；使用已有代理的方案只停止 `hit-daily`。

这一步保留 `news-data` 数据卷、默认方案的 Caddy 数据卷，以及安装目录里的 `.env`、`config.toml` 等文件。以后需要回退，可以在同一目录用原来的启动方式恢复。

如果你的共享反向代理另外配置了指向 `127.0.0.1:8765` 的新闻路由，可以在它的管理界面删除这一条路由；不需要停止整个共享代理。原公网 IP 仍可供服务器上的其他程序使用。

## 4. 可选：彻底删除本项目数据卷

仅在已经确认不再需要服务器历史，或已完成并保存上面的数据库备份后使用。**这一步不可撤销，删除本项目的新闻数据；默认方案还删除本项目的 Caddy 证书和配置数据卷。**

保持原安装目录与相同的项目配置，执行：

```bash
"${news_compose[@]}" down --volumes --timeout 150
```

这条命令只删除当前 Compose 项目声明的非外部卷，不会清理其他 Docker 项目的卷。不要改用 `docker system prune --volumes`、全局删除容器或全局删除卷的命令。

安装目录、下载的压缩包与本地镜像仍会保留，它们不会继续运行或调用 API。确认模型资料已填入手机、备份另存完成后，可用服务器文件管理器删除原 `hit-daily` 目录和不再需要的压缩包。无需卸载 Docker；MaiBot 等其他服务可能还在使用它。

## 如何确认已经停用

- `"${news_compose[@]}" ps -a` 不再列出该项目的运行容器。
- 旧新闻接口不再可用，但汐序 0.7.0 的“立即获取”仍可通过校园 VPN 在手机完成。
- 手机模型设置使用模型服务商的 HTTPS API 地址与模型密钥，不再填写旧服务器 IP 或 `NEWS_API_TOKEN`。

本指南没有在你的服务器上执行卸载操作；以上命令由你确认所在目录后自行运行。
