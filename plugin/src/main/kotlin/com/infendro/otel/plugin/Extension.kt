package com.infendro.otel.plugin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.Companion.ADAPTER_FOR_CALLABLE_REFERENCE
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class Extension() : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        // region helpers
        val unit = pluginContext.irBuiltIns.unitType
        val any = pluginContext.irBuiltIns.anyType
        val int = pluginContext.irBuiltIns.unitType
        val long = pluginContext.irBuiltIns.longType
        val float = pluginContext.irBuiltIns.floatType
        val double = pluginContext.irBuiltIns.doubleType
        val boolean = pluginContext.irBuiltIns.booleanType
        val char = pluginContext.irBuiltIns.charType
        val string = pluginContext.irBuiltIns.stringType

        fun IrBuilderWithScope.string(
            value: String
        ) = irString(value)

        fun IrBuilderWithScope.int(
            value: Int
        ) = irInt(value)

        fun IrBuilderWithScope.long(
            value: Long
        ) = irLong(value)

        fun IrBuilderWithScope.boolean(
            value: Boolean
        ) = irBoolean(value)

        val functions = mutableListOf<IrFunction>()

        fun getFunction(
            packageName: String,
            name: String,
            filter: (IrFunctionSymbol) -> Boolean = { true }
        ): IrFunctionSymbol {
            val callableId = CallableId(FqName(packageName), Name.identifier(name))
            return pluginContext.referenceFunctions(callableId).singleOrNull(filter)
                ?: throw Exception("function \"${packageName}/${name}\" not found")
        }

        fun getClass(
            packageName: String,
            name: String
        ): IrClassSymbol {
            val classId = ClassId(FqName(packageName), Name.identifier(name))
            return pluginContext.referenceClass(classId)
                ?: throw Exception("class \"${packageName}/${name}\" not found")
        }

        fun buildField(
            name: String,
            type: IrType,
            final: Boolean = false,
            static: Boolean = false,
            block: IrField.() -> Unit = {}
        ): IrField {
            return pluginContext.irFactory.buildField {
                this.name = Name.identifier(name)
                this.type = type
                this.isFinal = final
                this.isStatic = static
            }.apply(block)
        }

        fun buildFunction(
            name: String,
            returnType: IrType = unit,
            block: IrFunction.() -> Unit = {}
        ): IrFunction {
            return pluginContext.irFactory
                .buildFun {
                    this.name = Name.identifier(name)
                    this.returnType = returnType
                }
                .apply(block)
                .also(functions::add)
        }

        fun IrFunction.body(
            block: IrBlockBodyBuilder.() -> Unit
        ) {
            body = DeclarationIrBuilder(
                pluginContext,
                symbol,
                startOffset,
                endOffset
            ).irBlockBody {
                block()
            }
        }

        fun IrBlockBodyBuilder.call(
            function: IrFunctionSymbol,
            block: IrFunctionAccessExpression.() -> Unit = {}
        ) {
            +irCall(function).apply(block)
        }

        fun IrBlockBodyBuilder.call(
            function: IrFunction,
            block: IrFunctionAccessExpression.() -> Unit = {}
        ) = call(function.symbol, block)

        fun IrFunctionAccessExpression.argument(
            index: Int,
            value: IrExpression?
        ) {
            putValueArgument(index, value)
        }
        // endregion

        // region functions
        val printlnFunction = getFunction("kotlin.io", "println") {
            it.owner.valueParameters.size == 1 && it.owner.valueParameters.first().type == any.makeNullable()
        }

//        val awaitFunction = getFunction("com.infendro.otel.util", "await")

        // region build functions
        val function = buildFunction("_function") {
            body {
                call(printlnFunction) {
                    argument(0, string("test"))
                }
            }
        }

        val firstFile = moduleFragment.files[0]
        firstFile.addChild(function)
        // endregion
        // endregion

        fun IrFunction.modify() {
            body {
                call(function)
                for (statement in body!!.statements) +statement
            }
        }

        // region modify functions
        moduleFragment.files.forEach { file ->
            file.transform(
                object : IrElementTransformerVoidWithContext() {
                    override fun visitFunctionNew(function: IrFunction): IrStatement {
                        if (function in functions ||
                            function.body == null ||
                            function.origin == ADAPTER_FOR_CALLABLE_REFERENCE || //TODO ?
                            function.fqNameWhenAvailable?.asString()?.contains("<init>") != false || //TODO ?
                            function.fqNameWhenAvailable?.asString()?.contains("<anonymous>") != false //TODO ?
                        ) return function

                        function.modify()
                        return super.visitFunctionNew(function)
                    }
                },
                null
            )

            println("---${file.name}---")
            println(file.dump())
        }
        // endregion
    }
}
