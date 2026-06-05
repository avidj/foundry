```
flyctl launch --name foundry-server --region fra --no-deploy --dockerfile foundry-server/Dockerfile --build-only
API_KEY=$(openssl rand -hex 32)
echo $API_KEY
flyctl secrets set API_KEY=$API_KEY DATA_DIR=/data --app foundry-server
flyctl volumes create foundry_data --size 1 --region fra --app foundry-server
# 1. Create the volume (if not done yet)
flyctl volumes create foundry_data --size 1 --region fra --app foundry-server

# 2. Generate a deploy token for GitHub Actions
flyctl tokens create deploy -x 999999h
```
