'use client';

import type { ComponentProps } from 'react';
import React from 'react';
import {
    Controller,
    type ControllerProps,
    type FieldPath,
    type FieldValues,
    FormProvider,
    useFormContext,
} from 'react-hook-form';
import { cn } from '@/lib/utils.ts';

const Form = FormProvider;

type FormFieldContextValue<
    TFieldValues extends FieldValues = FieldValues,
    TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
> = {
    name: TName;
};

const FormFieldContext = React.createContext<FormFieldContextValue>(
    {} as FormFieldContextValue,
);

function FormField<
    TFieldValues extends FieldValues = FieldValues,
    TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
>({ ...props }: ControllerProps<TFieldValues, TName>) {
    return (
        <FormFieldContext.Provider value={{ name: props.name }}>
            <Controller {...props} />
        </FormFieldContext.Provider>
    );
}

function useFormField() {
    const fieldContext = React.useContext(FormFieldContext);
    const itemContext = React.useContext(FormItemContext);
    const { getFieldState, formState } = useFormContext();

    const fieldState = getFieldState(fieldContext.name, formState);

    if (!fieldContext) {
        throw new Error('useFormField must be used within a <FormField>');
    }

    const { id } = itemContext;

    return {
        id,
        name: fieldContext.name,
        formItemId: `${id}-form-item`,
        formDescriptionId: `${id}-form-item-description`,
        formMessageId: `${id}-form-item-message`,
        ...fieldState,
    };
}

type FormItemContextValue = {
    id: string;
};

const FormItemContext = React.createContext<FormItemContextValue>(
    {} as FormItemContextValue,
);

function FormItem({ className, ref, ...props }: ComponentProps<'div'>) {
    const id = React.useId();

    return (
        <FormItemContext.Provider value={{ id }}>
            <div className={cn('space-y-2', className)} ref={ref} {...props} />
        </FormItemContext.Provider>
    );
}

function FormLabel({ className, ref, ...props }: ComponentProps<'label'>) {
    const { error, formItemId } = useFormField();

    return (
        // biome-ignore lint/a11y/noLabelWithoutControl: htmlFor={formItemId} points to the FormControl wrapper which contains the actual input; this is the standard shadcn/RHF form pattern
        <label
            className={cn(
                'text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70',
                error && 'text-error-dark',
                className,
            )}
            htmlFor={formItemId}
            ref={ref}
            {...props}
        />
    );
}

function FormControl({ ref, ...props }: ComponentProps<'div'>) {
    const { error, formItemId, formDescriptionId, formMessageId } =
        useFormField();

    return (
        <div
            aria-describedby={
                error
                    ? `${formDescriptionId} ${formMessageId}`
                    : formDescriptionId
            }
            aria-invalid={!!error}
            id={formItemId}
            ref={ref}
            {...props}
        />
    );
}

function FormDescription({ className, ref, ...props }: ComponentProps<'p'>) {
    const { formDescriptionId } = useFormField();

    return (
        <p
            className={cn('text-sm text-text-subtle', className)}
            id={formDescriptionId}
            ref={ref}
            {...props}
        />
    );
}

function FormMessage({
    className,
    children,
    ref,
    ...props
}: ComponentProps<'p'>) {
    const { error, formMessageId } = useFormField();
    const body = error ? String(error.message ?? '') : children;

    if (!body) {
        return null;
    }

    return (
        <p
            className={cn('text-sm font-medium text-error-dark', className)}
            id={formMessageId}
            ref={ref}
            role='alert'
            {...props}
        >
            {body}
        </p>
    );
}

export {
    Form,
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
    useFormField,
};
