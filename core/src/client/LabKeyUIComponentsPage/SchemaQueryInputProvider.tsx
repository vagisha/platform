import React, {ChangeEvent} from 'react';
import {Col, FormControl, Row, Button} from "react-bootstrap";
import {Alert, getStateQueryGridModel, QueryConfig, QueryGridModel, SchemaQuery} from "@labkey/components";

const Context = React.createContext<SchemaQueryInputContext>(undefined);
const SchemaQueryInputContextProvider = Context.Provider;

interface Props {}

interface State {
    schemaName: string,
    queryName: string,
    error: string,
    model: QueryGridModel,
    queryConfig: QueryConfig,
}

export type SchemaQueryInputContext = State;

export const SchemaQueryInputProvider = (Component: React.ComponentType) => {
    return class SchemaQueryInputProviderImpl extends React.Component<Props, State> {
        readonly state: State = {
            queryName: undefined,
            schemaName: undefined,
            error: undefined,
            model: undefined,
            queryConfig: undefined,
        };

        onFormChange = (e: ChangeEvent<HTMLInputElement>) => {
            const { name, value } = e.target;
            this.setState(() => ({
                ...this.state,
                error: undefined,
                model: undefined,
                queryConfig: undefined,
                [name]: value
            }));
        };

        onApply = () => {
            const { schemaName, queryName } = this.state;

            let error, model, queryConfig;
            if (!schemaName || !queryName) {
                error = 'You must enter a schema/query to view the QueryGridPanel.'
            }
            else {
                const schemaQuery = SchemaQuery.create(schemaName, queryName);
                model = getStateQueryGridModel('components-querygridmodel', schemaQuery, {isPaged: true});
                queryConfig = { id: `components-queryconfig-${schemaName}-${queryName}`, schemaQuery };
            }

            this.setState(() => ({model, queryConfig, error}));
        };

        render() {
            const { error } = this.state;

            return (
                <SchemaQueryInputContextProvider value={this.state}>
                    <Row>
                        <Col xs={4}>Schema: <FormControl name={'schemaName'} type="text" onChange={this.onFormChange}/></Col>
                        <Col xs={4}>Query: <FormControl name={'queryName'} type="text" onChange={this.onFormChange}/></Col>
                        <Col xs={4}><Button onClick={this.onApply}>Apply</Button></Col>
                    </Row>
                    <br/>
                    {error && <Alert>{error}</Alert>}
                    <Component {...this.props} {...this.state}/>
                </SchemaQueryInputContextProvider>
            )
        }
    }
};