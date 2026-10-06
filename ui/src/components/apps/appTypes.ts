export type AppStage = "OPEN" | "RUNNING" | "SUCCESS" | "FAILURE"

export interface AppBlock {
    type: string;
    content?: string;
    text?: string;
    style?: string;
}

export interface App {
    id: string;
    namespace: string;
    displayName?: string;
    description?: string;
    flowId: string;
    layout: {on: AppStage; blocks: AppBlock[]}[];
    source: string;
}

export const APP_TEMPLATE = `id: my-app
namespace: company.team
displayName: My app
flowId: my-flow
layout:
  - on: OPEN
    blocks:
      - type: Markdown
        content: "## Fill in the form and submit"
      - type: CreateExecutionForm
      - type: CreateExecutionButton
        text: Submit
  - on: RUNNING
    blocks:
      - type: Loading
      - type: Logs
  - on: SUCCESS
    blocks:
      - type: Alert
        style: SUCCESS
        content: Done!
      - type: Outputs
  - on: FAILURE
    blocks:
      - type: Alert
        style: ERROR
        content: Something went wrong.
      - type: Logs
`
