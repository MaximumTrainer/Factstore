import client from './client'

export interface LiveArtifactDeployment {
  environmentId: string
  environmentName: string
  imageTag: string
  sha256Digest: string
  snapshotCreatedAt: string
}

export interface LiveArtifactByRepo {
  imageName: string
  deployments: LiveArtifactDeployment[]
}

export function getLiveArtifactsByRepo() {
  return client.get<LiveArtifactByRepo[]>('/environments/live-artifacts')
}
